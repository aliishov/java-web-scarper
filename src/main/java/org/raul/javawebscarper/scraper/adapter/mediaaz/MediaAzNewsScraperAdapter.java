package org.raul.javawebscarper.scraper.adapter.mediaaz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.raul.javawebscarper.browser.BrowserEngineException;
import org.raul.javawebscarper.browser.BrowserPage;
import org.raul.javawebscarper.browser.BrowserSession;
import org.raul.javawebscarper.browser.BrowserSessionFactory;
import org.raul.javawebscarper.browser.BrowserTimeoutException;
import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.scraper.adapter.NewsScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionStatus;
import org.raul.javawebscarper.scraper.support.DateRangeValidator;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaAzNewsScraperAdapter implements NewsScraperAdapter {

	private static final String SEARCH_TOGGLE_SELECTOR = "button.header__tool.header__search-open";
	private static final String SEARCH_INPUT_SELECTOR = "form.header__search input[name='query']";
	private static final String RESULT_CARD_SELECTOR = "div.post-block[data-timestamp]";
	private static final int MIN_ARTICLE_TEXT_LENGTH = 50;
	private static final int MIN_PARAGRAPH_TEXT_LENGTH = 20;
	private static final String ARTICLE_DATE_SELECTOR = ".news-inner__info li";
	private static final String ARTICLE_TITLE_SELECTOR = ".news-inner__title";
	private static final String ARTICLE_TEXT_SELECTOR = ".news-inner__desc";
	private static final String ARTICLE_MAIN_IMAGE_SELECTOR = String.join(", ",
			".news-inner__image img[src]",
			".news-inner__image img[data-src]",
			".news-inner__image img[srcset]",
			".news-inner__image img[data-srcset]"
	);
	private static final String CLEANUP_SELECTOR = String.join(", ",
			"script",
			"style",
			"iframe",
			"ins",
			"noscript",
			"template",
			".seo__tags",
			"[class*=share]",
			"[class*=related]",
			"[class*=similar]",
			"[class*=category]",
			"[class*=label]",
			"[class*=tag]",
			"[class*=advert]",
			"[class*=banner]",
			"[class*=sidebar]",
			"[id*=yandex]",
			"[class*=yandex]",
			"[href*=yandex]",
			"[src*=yandex]",
			"[src*='dsps.newmedia.az']",
			"[href*='dsps.newmedia.az']",
			"[data-ad-id]",
			"[data-name=adWrapper]"
	);
	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"media.az",
			"media.az",
			"Media.az",
			MediaAzScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final MediaAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return MediaAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return MediaAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < 3) {
			return ScraperExecutionResult.failed("media.az search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		log.info(
				"Starting media.az scraping: keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
				keyword,
				context.dateFrom(),
				context.dateTo(),
				context.maxPages(),
				context.maxPosts()
		);

		try (BrowserSession session = browserSessionFactory.createSession()) {
			BrowserPage page = session.newPage();
			openSearchPage(page, keyword.trim(), context);
			Optional<ScraperExecutionResult> blockedResult = failIfAntiBotPage(page, "UI search");
			if (blockedResult.isPresent()) {
				return blockedResult.get();
			}
			List<MediaAzSearchResultCard> cards = collectSearchResultCards(page, context);
			if (cards.isEmpty()) {
				String fallbackUrl = datedSearchUrl(
						keyword.trim(),
						context.dateFrom().toLocalDate(),
						context.dateTo().toLocalDate(),
						false
				);
				log.info("media.az dated search returned no cards, retrying with empty date_end: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
				waitForSearchResults(page);
				blockedResult = failIfAntiBotPage(page, "dated search fallback");
				if (blockedResult.isPresent()) {
					return blockedResult.get();
				}
				cards = collectSearchResultCards(page, context);
			}
			if (cards.isEmpty()) {
				String broadFallbackUrl = searchUrl(keyword.trim());
				log.info("media.az dated fallbacks returned no cards, retrying broad search URL: {}", broadFallbackUrl);
				page.navigate(broadFallbackUrl);
				page.waitForTimeout(1_000);
				waitForSearchResults(page);
				blockedResult = failIfAntiBotPage(page, "broad search fallback");
				if (blockedResult.isPresent()) {
					return blockedResult.get();
				}
				cards = collectSearchResultCards(page, context);
			}
			MediaAzArticleCollectionResult result = collectArticles(page, cards, context);
			log.info(
					"Finished media.az scraping: keyword={}, candidatesFound={}, articlesOpened={}, "
							+ "postsFound={}, emptyText={}, outOfRange={}, duplicates={}, errors={}",
					keyword,
					cards.size(),
					result.articlesOpened(),
					result.posts().size(),
					result.skippedEmptyText(),
					result.skippedOutOfRange(),
					result.duplicates(),
					result.skippedErrors()
			);
			if (!result.posts().isEmpty()) {
				return ScraperExecutionResult.success(result.posts());
			}
			if (cards.isEmpty()) {
				return ScraperExecutionResult.empty();
			}
			if (result.hasExtractionFailures()) {
				return result.toFailedScraperResult(cards.size());
			}
			return ScraperExecutionResult.empty();
		} catch (BrowserEngineException exception) {
			log.warn("media.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("media.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected media.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected media.az scraping failure: " + exception.getMessage());
		}
	}

	private void openSearchPage(BrowserPage page, String keyword, ScraperExecutionContext context) {
		String fallbackUrl = datedSearchUrl(
				keyword,
				context.dateFrom().toLocalDate(),
				context.dateTo().toLocalDate(),
				true
		);
		try {
			log.info("Opening media.az search through UI flow: keyword={}", keyword);
			page.navigate(MediaAzScraperSupport.BASE_URL);
			page.click(SEARCH_TOGGLE_SELECTOR);
			page.fill(SEARCH_INPUT_SELECTOR, keyword);
			page.press(SEARCH_INPUT_SELECTOR, "Enter");
			page.waitForTimeout(1_000);
			if (!page.url().contains("/search") || !page.url().contains("query")) {
				log.warn("media.az UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
				waitForSearchResults(page);
			} else {
				log.info("media.az UI search page opened, collecting UI results before dated fallbacks");
				waitForSearchResults(page);
			}
		} catch (BrowserEngineException exception) {
			log.warn("media.az UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
			waitForSearchResults(page);
		}
		log.info("media.az search page opened: {}", page.url());
	}

	private Optional<ScraperExecutionResult> failIfAntiBotPage(BrowserPage page, String step) {
		if (!MediaAzScraperSupport.isLikelyAntiBotPage(page.content())) {
			return Optional.empty();
		}
		String message = "media.az anti-bot challenge detected during " + step;
		log.warn("{}: url={}", message, page.url());
		return Optional.of(ScraperExecutionResult.failed(message));
	}

	private void waitForSearchResults(BrowserPage page) {
		try {
			page.waitForSelector(RESULT_CARD_SELECTOR, 5_000);
		} catch (BrowserTimeoutException exception) {
			log.debug("media.az search result cards did not appear before timeout: url={}", page.url());
		}
	}

	String searchUrl(String keyword) {
		return MediaAzScraperSupport.BASE_URL + "search?query="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8);
	}

	String datedSearchUrl(String keyword, LocalDate dateFrom, LocalDate dateTo, boolean includeDateEnd) {
		String dateEnd = includeDateEnd && dateTo != null ? dateTo.toString() : "";
		return searchUrl(keyword)
				+ "&date_start=" + (dateFrom == null ? "" : dateFrom)
				+ "&date_end=" + dateEnd
				+ "&category=&sort_type=0";
	}

	private List<MediaAzSearchResultCard> collectSearchResultCards(
			BrowserPage page,
			ScraperExecutionContext context
	) {
		Map<String, MediaAzSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();

		for (int scroll = 0; scroll < context.maxPages() && cardsByUrl.size() < context.maxPosts(); scroll++) {
			CardCollectionStats stats = collectCurrentCards(page, context, cardsByUrl, seenUrls);
			log.info(
					"media.az search cards pass={}: found={}, added={}, duplicateCards={}, "
							+ "skippedMissingUrl={}, skippedDateParse={}, beforeRange={}, afterRange={}",
					scroll + 1,
					stats.found(),
					stats.added(),
					stats.duplicates(),
					stats.skippedMissingUrl(),
					stats.skippedDateParse(),
					stats.beforeRange(),
					stats.afterRange()
			);

			if (stats.found() == 0 || stats.onlyNewCardsAreOld() || cardsByUrl.size() >= context.maxPosts()) {
				break;
			}
			if (scroll + 1 >= context.maxPages()) {
				break;
			}
			page.scrollToBottom(1, 1_000);
			page.waitForTimeout(750);
		}
		if (!cardsByUrl.isEmpty()) {
			log.info("media.az candidate URLs: {}", cardsByUrl.keySet().stream().limit(10).toList());
		}
		return cardsByUrl.values().stream().limit(context.maxPosts()).toList();
	}

	private CardCollectionStats collectCurrentCards(
			BrowserPage page,
			ScraperExecutionContext context,
			Map<String, MediaAzSearchResultCard> cardsByUrl,
			Set<String> seenUrls
	) {
		Document document = Jsoup.parse(page.content(), MediaAzScraperSupport.BASE_URL);
		Elements elements = document.select(RESULT_CARD_SELECTOR);
		int found = elements.size();
		int added = 0;
		int duplicates = 0;
		int beforeRange = 0;
		int afterRange = 0;
		int skippedMissingUrl = 0;
		int skippedDateParse = 0;
		int newDatedCards = 0;

		for (Element element : elements) {
			Optional<MediaAzSearchResultCard> parsedCard = parseResultCard(element);
			if (parsedCard.isEmpty()) {
				if (hasResultUrl(element)) {
					skippedDateParse++;
				} else {
					skippedMissingUrl++;
				}
				continue;
			}
			MediaAzSearchResultCard card = parsedCard.get();
			if (!seenUrls.add(card.postUrl())) {
				duplicates++;
				continue;
			}
			newDatedCards++;
			if (DateRangeValidator.isAfterRange(card.postDate(), context.dateTo())) {
				afterRange++;
				continue;
			}
			if (DateRangeValidator.isBeforeRange(card.postDate(), context.dateFrom())) {
				beforeRange++;
				continue;
			}
			cardsByUrl.put(card.postUrl(), card);
			added++;
			if (cardsByUrl.size() >= context.maxPosts()) {
				break;
			}
		}
		return new CardCollectionStats(
				found,
				added,
				duplicates,
				beforeRange,
				afterRange,
				skippedMissingUrl,
				skippedDateParse,
				newDatedCards
		);
	}

	Optional<MediaAzSearchResultCard> parseResultCard(Element element) {
		String postUrl = attr(element, "a.news__item[href]", "href");
		if (postUrl == null) {
			return Optional.empty();
		}
		String normalizedUrl = MediaAzScraperSupport.normalizePostUrl(postUrl);
		Optional<OffsetDateTime> postDate = dateParser.parseResultCardTimestamp(element.attr("data-timestamp"));
		if (postDate.isEmpty()) {
			postDate = dateParser.parseVisibleDate(text(element, ".news__date"));
		}
		if (postDate.isEmpty()) {
			log.warn(
					"Skipping media.az card with unparseable date: url={}, dataTimestamp={}, visibleDate={}",
					normalizedUrl,
					element.attr("data-timestamp"),
					text(element, ".news__date")
			);
			return Optional.empty();
		}
		String thumbnailUrl = Optional.ofNullable(element.selectFirst(".news__image img"))
				.map(image -> firstNonBlank(image.attr("src"), image.attr("data-src")))
				.filter(value -> !value.isBlank())
				.map(value -> UrlNormalizer.resolve(MediaAzScraperSupport.BASE_URL, value))
				.filter(MediaAzScraperSupport::isAllowedMediaUrl)
				.orElse(null);

		return Optional.of(new MediaAzSearchResultCard(
				normalizedUrl,
				firstNonBlank(
						text(element, ".news__title"),
						text(element, ".news__item-title"),
						text(element, ".news__item h3"),
						text(element, ".news__item h2")
				),
				postDate.get(),
				thumbnailUrl
		));
	}

	private MediaAzArticleCollectionResult collectArticles(
			BrowserPage page,
			List<MediaAzSearchResultCard> cards,
			ScraperExecutionContext context
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenExternalIds = new LinkedHashSet<>();
		Set<String> seenPostUrls = new LinkedHashSet<>();
		int articlesOpened = 0;
		int skippedEmptyText = 0;
		int skippedOutOfRange = 0;
		int skippedErrors = 0;
		int duplicates = 0;

		for (MediaAzSearchResultCard card : cards) {
			if (posts.size() >= context.maxPosts()) {
				break;
			}
			articlesOpened++;
			try {
				ArticleCollectionAttempt attempt = collectArticleWithRetry(page, card, context);
				if (attempt.post() == null) {
					if (attempt.skipReason() == ArticleSkipReason.EMPTY_TEXT) {
						skippedEmptyText++;
					} else if (attempt.skipReason() == ArticleSkipReason.OUT_OF_RANGE) {
						skippedOutOfRange++;
					}
					continue;
				}
				ScrapedPostDTO post = attempt.post();
				if (seenExternalIds.add(post.externalPostId()) && seenPostUrls.add(post.postUrl())) {
					posts.add(post);
				} else {
					duplicates++;
					log.debug("Skipping duplicate media.az article: url={}, externalPostId={}", post.postUrl(), post.externalPostId());
				}
			} catch (RuntimeException exception) {
				skippedErrors++;
				log.warn("Skipping media.az article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return new MediaAzArticleCollectionResult(
				posts,
				articlesOpened,
				skippedEmptyText,
				skippedOutOfRange,
				skippedErrors,
				duplicates
		);
	}

	private ArticleCollectionAttempt collectArticleWithRetry(
			BrowserPage page,
			MediaAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		try {
			return collectArticle(page, card, context);
		} catch (BrowserTimeoutException exception) {
			log.warn("Timeout opening media.az article, retrying once: url={}", card.postUrl());
			return collectArticle(page, card, context);
		}
	}

	private ArticleCollectionAttempt collectArticle(
			BrowserPage page,
			MediaAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		page.navigate(card.postUrl());
		page.waitForTimeout(500);
		Document document = Jsoup.parse(page.content(), card.postUrl());
		OffsetDateTime postDate = parseArticleDate(document).orElse(card.postDate());
		if (!DateRangeValidator.isInsideRange(postDate, context.dateFrom(), context.dateTo())) {
			log.debug("Skipping media.az article outside date range: url={}, postDate={}", card.postUrl(), postDate);
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.OUT_OF_RANGE);
		}

		String text = extractArticleText(document, card);
		if (text == null || text.isBlank()) {
			log.warn("Skipping media.az article with empty text: url={}, title={}", card.postUrl(), card.title());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.EMPTY_TEXT);
		}

		String externalPostId = MediaAzScraperSupport.extractExternalPostId(card.postUrl())
				.orElseGet(() -> Integer.toHexString(card.postUrl().hashCode()));
		String title = firstNonBlank(text(document, ARTICLE_TITLE_SELECTOR), card.title());
		List<ScrapedMediaDTO> media = extractMedia(document, card);
		Map<String, Object> metadata = metadata(context, card, title);

		log.info("Collected media.az article: url={}, mediaCount={}", card.postUrl(), media.size());
		return ArticleCollectionAttempt.collected(new ScrapedPostDTO(
				externalPostId,
				card.postUrl(),
				postDate,
				AUTHOR,
				text,
				"ru",
				media,
				metadata
		));
	}

	Optional<OffsetDateTime> parseArticleDate(Document document) {
		for (Element element : document.select(ARTICLE_DATE_SELECTOR)) {
			Optional<OffsetDateTime> parsedDate = dateParser.parseArticleDate(element.text());
			if (parsedDate.isPresent()) {
				return parsedDate;
			}
		}
		return Optional.empty();
	}

	String extractArticleText(Document document, MediaAzSearchResultCard card) {
		Element content = document.selectFirst(ARTICLE_TEXT_SELECTOR);
		if (content == null) {
			return firstMetaContent(
					document,
					"meta[name=description]",
					"meta[property=description]",
					"meta[property=og:description]"
			).map(this::normalizeArticleText)
					.filter(this::isUsableArticleText)
					.orElse(null);
		}
		Element cleanContent = cleanedArticleContent(content);
		List<String> paragraphs = cleanContent
				.select("p")
				.stream()
				.map(Element::text)
				.map(this::normalizeArticleText)
				.filter(this::isArticleParagraph)
				.toList();
		String text = String.join("\n", paragraphs);
		if (isUsableArticleText(text)) {
			return text;
		}
		return firstMetaContent(
				document,
				"meta[name=description]",
				"meta[property=description]",
				"meta[property=og:description]"
		).map(this::normalizeArticleText)
				.filter(this::isUsableArticleText)
				.orElse(null);
	}

	private Element cleanedArticleContent(Element root) {
		Element cleanRoot = root.clone();
		cleanRoot.select(CLEANUP_SELECTOR).remove();
		cleanRoot.select("*")
				.stream()
				.filter(this::isNumericAdElement)
				.toList()
				.forEach(Element::remove);
		return cleanRoot;
	}

	private boolean isNumericAdElement(Element element) {
		String text = normalizeArticleText(element.ownText());
		return text != null && text.matches("\\d+");
	}

	private boolean isArticleParagraph(String text) {
		if (text == null || text.length() < MIN_PARAGRAPH_TEXT_LENGTH) {
			return false;
		}
		String normalized = normalizeForNoiseChecks(text);
		return !containsAdvertisingNoise(normalized)
				&& !normalized.contains("tegi")
				&& !normalized.contains("podpisyvaytes");
	}

	private boolean containsAdvertisingNoise(String normalizedText) {
		return normalizedText.contains("dsps.newmedia.az")
				|| normalizedText.contains("yandex")
				|| normalizedText.contains("doubleclick")
				|| normalizedText.contains("googleads")
				|| normalizedText.contains("reklam")
				|| normalizedText.contains("banner")
				|| normalizedText.contains("advert")
				|| normalizedText.contains("podpisyvajtes")
				|| normalizedText.contains("novosti na nashem kanale");
	}

	private boolean isUsableArticleText(String text) {
		return text != null && text.length() >= MIN_ARTICLE_TEXT_LENGTH;
	}

	private String normalizeArticleText(String text) {
		if (text == null) {
			return null;
		}
		String normalized = text
				.replace('\u00A0', ' ')
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.replaceAll(" *\\n+ *", "\n")
				.trim();
		List<String> lines = normalized.lines()
				.map(String::trim)
				.filter(line -> !line.isBlank())
				.toList();
		return String.join("\n", lines);
	}

	List<ScrapedMediaDTO> extractMedia(Document document, MediaAzSearchResultCard card) {
		Optional<String> mainImageUrl = extractMainImageUrl(document, card.postUrl())
				.or(() -> extractMetaImageUrl(document, card.postUrl()));
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		mainImageUrl.ifPresent(mediaUrl -> mediaByUrl.put(mediaUrl, MediaType.IMAGE));
		if (mediaByUrl.isEmpty() && card.thumbnailUrl() != null) {
			mediaByUrl.put(card.thumbnailUrl(), MediaType.IMAGE);
		}

		List<ScrapedMediaDTO> media = new ArrayList<>();
		int position = 0;
		for (Map.Entry<String, MediaType> entry : mediaByUrl.entrySet()) {
			media.add(new ScrapedMediaDTO(entry.getKey(), entry.getValue(), position++));
		}
		return media;
	}

	private Optional<String> extractMainImageUrl(Document document, String baseUrl) {
		Element image = document.selectFirst(ARTICLE_MAIN_IMAGE_SELECTOR);
		if (image == null) {
			return Optional.empty();
		}
		String rawUrl = firstNonBlank(
				image.attr("src"),
				image.attr("data-src"),
				firstSrcsetUrl(image.attr("srcset")),
				firstSrcsetUrl(image.attr("data-srcset"))
		);
		if (rawUrl == null || !MediaAzScraperSupport.isAllowedMediaUrl(rawUrl)) {
			return Optional.empty();
		}
		String normalizedUrl = UrlNormalizer.resolve(baseUrl, rawUrl);
		return MediaAzScraperSupport.isAllowedMediaUrl(normalizedUrl)
				? Optional.of(normalizedUrl)
				: Optional.empty();
	}

	private Optional<String> extractMetaImageUrl(Document document, String baseUrl) {
		return firstMetaContent(
				document,
				"meta[property=og:image]",
				"meta[name=twitter:image]"
		).map(rawUrl -> UrlNormalizer.resolve(baseUrl, rawUrl))
				.filter(MediaAzScraperSupport::isAllowedMediaUrl);
	}

	private String firstSrcsetUrl(String srcset) {
		if (srcset == null || srcset.isBlank()) {
			return null;
		}
		String firstCandidate = srcset.split(",")[0].trim();
		if (firstCandidate.isBlank()) {
			return null;
		}
		return firstCandidate.split("\\s+")[0].trim();
	}

	private Map<String, Object> metadata(ScraperExecutionContext context, MediaAzSearchResultCard card, String title) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", MediaAzScraperSupport.SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		if (title != null && !title.isBlank()) {
			metadata.put("title", title);
		}
		return metadata;
	}

	private boolean hasResultUrl(Element element) {
		return attr(element, "a.news__item[href]", "href") != null;
	}

	private String attr(Element element, String selector, String attribute) {
		Element selected = element.selectFirst(selector);
		return selected == null ? null : selected.attr(attribute).trim();
	}

	private String text(Element element, String selector) {
		Element selected = element.selectFirst(selector);
		return selected == null ? null : selected.text().trim();
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private Optional<String> firstMetaContent(Document document, String... selectors) {
		for (String selector : selectors) {
			Element element = document.selectFirst(selector);
			if (element == null) {
				continue;
			}
			String content = firstNonBlank(element.attr("content"));
			if (content != null) {
				return Optional.of(content);
			}
		}
		return Optional.empty();
	}

	private String normalizeForNoiseChecks(String value) {
		return value.toLowerCase(Locale.ROOT)
				.replace("ё", "е")
				.replace("й", "и")
				.replace("ə", "e")
				.replace("Ã©â„¢", "e")
				.replace("É™", "e");
	}

	private enum ArticleSkipReason {
		EMPTY_TEXT,
		OUT_OF_RANGE
	}

	private record ArticleCollectionAttempt(
			ScrapedPostDTO post,
			ArticleSkipReason skipReason
	) {

		private static ArticleCollectionAttempt collected(ScrapedPostDTO post) {
			return new ArticleCollectionAttempt(post, null);
		}

		private static ArticleCollectionAttempt skipped(ArticleSkipReason reason) {
			return new ArticleCollectionAttempt(null, reason);
		}
	}

	private record MediaAzArticleCollectionResult(
			List<ScrapedPostDTO> posts,
			int articlesOpened,
			int skippedEmptyText,
			int skippedOutOfRange,
			int skippedErrors,
			int duplicates
	) {

		private boolean hasExtractionFailures() {
			return skippedEmptyText > 0 || skippedErrors > 0;
		}

		private ScraperExecutionResult toFailedScraperResult(int candidatesFound) {
			String message = ("media.az extraction failed: candidatesFound=%d, articlesOpened=%d, "
					+ "articlesSkippedEmptyText=%d, articlesSkippedOutOfRange=%d, "
					+ "articlesSkippedErrors=%d, duplicateArticles=%d, postsCollected=0")
					.formatted(
							candidatesFound,
							articlesOpened,
							skippedEmptyText,
							skippedOutOfRange,
							skippedErrors,
							duplicates
					);
			return new ScraperExecutionResult(
					ScraperExecutionStatus.FAILED,
					List.of(),
					0,
					articlesOpened,
					message,
					null,
					null
			);
		}
	}

	private record CardCollectionStats(
			int found,
			int added,
			int duplicates,
			int beforeRange,
			int afterRange,
			int skippedMissingUrl,
			int skippedDateParse,
			int newDatedCards
	) {

		private boolean onlyNewCardsAreOld() {
			return newDatedCards > 0 && beforeRange == newDatedCards;
		}
	}
}
