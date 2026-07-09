package org.raul.javawebscarper.scraper.adapter.oxuaz;

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
public class OxuAzNewsScraperAdapter implements NewsScraperAdapter {

	private static final String SEARCH_TOGGLE_SELECTOR = "button.custom-navbar-search-toggle";
	private static final String SEARCH_INPUT_SELECTOR = "form[action^='/all'] input[name='query']";
	private static final String RESULT_CARD_SELECTOR = String.join(", ",
			"div.rt-news-item[data-url]",
			"div.col-12.col-sm-6.col-md-6.col-lg-6.rt-news-item"
	);
	private static final int MIN_ARTICLE_TEXT_LENGTH = 50;
	private static final int MIN_PARAGRAPH_TEXT_LENGTH = 20;
	private static final String ARTICLE_DATE_SELECTOR = String.join(", ",
			"article time[datetime]",
			"main time[datetime]",
			"article time",
			"main time",
			".post-item-meta span",
			".post-detail-date",
			".news-inner__info li",
			"[class*='date']",
			"[class*='time']"
	);
	private static final String[] ARTICLE_ROOT_SELECTORS = {
			"article .post-detail-content",
			"article .news-inner__desc",
			"article [class*='content']",
			"article [class*='detail']",
			".post-detail-content",
			".news-inner__desc",
			"[class*='detail'] [class*='content']",
			"main article",
			"article",
			".rt-news-item[data-url]"
	};
	private static final String CLEANUP_SELECTOR = String.join(", ",
			"script",
			"style",
			"nav",
			"footer",
			"aside",
			"header",
			"form",
			"iframe",
			"ins",
			"noscript",
			"template",
			".seo__tags",
			"[class*=share]",
			"[class*=related]",
			"[class*=similar]",
			"[class*=sidebar]",
			"[class*=advert]",
			"[class*=banner]",
			"[class*=category]",
			"[class*=breadcrumb]",
			"[class*=tag]",
			"[class*=label]",
			"[id*=yandex]",
			"[class*=yandex]",
			"[href*=yandex]",
			"[src*=yandex]",
			"[data-ad-id]",
			"[data-name=adWrapper]"
	);
	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"oxu.az",
			"oxu.az",
			"Oxu.az",
			OxuAzScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final OxuAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return OxuAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return OxuAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < 3) {
			return ScraperExecutionResult.failed("oxu.az search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		log.info(
				"Starting oxu.az scraping: keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
				keyword,
				context.dateFrom(),
				context.dateTo(),
				context.maxPages(),
				context.maxPosts()
		);

		try (BrowserSession session = browserSessionFactory.createSession()) {
			BrowserPage page = session.newPage();
			openSearchPage(page, keyword.trim());
			List<OxuAzSearchResultCard> cards = collectSearchResultCards(page, context);
			OxuAzArticleCollectionResult result = collectArticles(page, cards, context);
			log.info(
					"Finished oxu.az scraping: keyword={}, candidatesFound={}, articlesOpened={}, "
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
			log.warn("oxu.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("oxu.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected oxu.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected oxu.az scraping failure: " + exception.getMessage());
		}
	}

	private void openSearchPage(BrowserPage page, String keyword) {
		String fallbackUrl = searchUrl(keyword);
		try {
			log.info("Opening oxu.az search through UI flow: keyword={}", keyword);
			page.navigate(OxuAzScraperSupport.BASE_URL);
			page.click(SEARCH_TOGGLE_SELECTOR);
			page.fill(SEARCH_INPUT_SELECTOR, keyword);
			page.press(SEARCH_INPUT_SELECTOR, "Enter");
			page.waitForTimeout(1_000);
			if (!page.url().contains("/all") || !page.url().contains("query")) {
				log.warn("oxu.az UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			}
		} catch (BrowserEngineException exception) {
			log.warn("oxu.az UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
		}
		log.info("oxu.az search page opened: {}", page.url());
	}

	String searchUrl(String keyword) {
		return OxuAzScraperSupport.BASE_URL + "all?query="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8);
	}

	private List<OxuAzSearchResultCard> collectSearchResultCards(
			BrowserPage page,
			ScraperExecutionContext context
	) {
		Map<String, OxuAzSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();

		for (int scroll = 0; scroll < context.maxPages() && cardsByUrl.size() < context.maxPosts(); scroll++) {
			CardCollectionStats stats = collectCurrentCards(page, context, cardsByUrl, seenUrls);
			log.info(
					"oxu.az search cards pass={}: found={}, added={}, duplicateCards={}, "
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
			log.info("oxu.az candidate URLs: {}", cardsByUrl.keySet().stream().limit(10).toList());
		}
		return cardsByUrl.values().stream().limit(context.maxPosts()).toList();
	}

	private CardCollectionStats collectCurrentCards(
			BrowserPage page,
			ScraperExecutionContext context,
			Map<String, OxuAzSearchResultCard> cardsByUrl,
			Set<String> seenUrls
	) {
		Document document = Jsoup.parse(page.content(), OxuAzScraperSupport.BASE_URL);
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
			Optional<OxuAzSearchResultCard> parsedCard = parseResultCard(element);
			if (parsedCard.isEmpty()) {
				if (hasResultUrl(element)) {
					skippedDateParse++;
				} else {
					skippedMissingUrl++;
				}
				continue;
			}
			OxuAzSearchResultCard card = parsedCard.get();
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

	Optional<OxuAzSearchResultCard> parseResultCard(Element element) {
		String postUrl = firstNonBlank(
				element.attr("data-url"),
				attr(element, "h2.post-item-title a[href]", "href"),
				attr(element, ".post-item-img a[href]", "href")
		);
		if (postUrl == null) {
			return Optional.empty();
		}
		String normalizedUrl = OxuAzScraperSupport.normalizePostUrl(postUrl);
		String dateText = text(element, ".post-item-meta span");
		Optional<OffsetDateTime> postDate = dateParser.parseResultCardDate(dateText);
		if (postDate.isEmpty()) {
			log.warn("Skipping oxu.az card with unparseable date: url={}, dateText={}", normalizedUrl, dateText);
			return Optional.empty();
		}
		String thumbnailUrl = Optional.ofNullable(element.selectFirst(".post-item-img img"))
				.map(image -> firstNonBlank(image.attr("src"), image.attr("data-src")))
				.filter(value -> !value.isBlank())
				.map(value -> UrlNormalizer.resolve(OxuAzScraperSupport.BASE_URL, value))
				.filter(OxuAzScraperSupport::isAllowedMediaUrl)
				.orElse(null);

		return Optional.of(new OxuAzSearchResultCard(
				normalizedUrl,
				text(element, "h2.post-item-title a"),
				postDate.get(),
				thumbnailUrl
		));
	}

	private OxuAzArticleCollectionResult collectArticles(
			BrowserPage page,
			List<OxuAzSearchResultCard> cards,
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

		for (OxuAzSearchResultCard card : cards) {
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
					log.debug("Skipping duplicate oxu.az article: url={}, externalPostId={}", post.postUrl(), post.externalPostId());
				}
			} catch (RuntimeException exception) {
				skippedErrors++;
				log.warn("Skipping oxu.az article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return new OxuAzArticleCollectionResult(
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
			OxuAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		try {
			return collectArticle(page, card, context);
		} catch (BrowserTimeoutException exception) {
			log.warn("Timeout opening oxu.az article, retrying once: url={}", card.postUrl());
			return collectArticle(page, card, context);
		}
	}

	private ArticleCollectionAttempt collectArticle(
			BrowserPage page,
			OxuAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		page.navigate(card.postUrl());
		page.waitForTimeout(500);
		Document document = Jsoup.parse(page.content(), card.postUrl());
		OffsetDateTime postDate = parseArticleDate(document).orElse(card.postDate());
		if (!DateRangeValidator.isInsideRange(postDate, context.dateFrom(), context.dateTo())) {
			log.debug("Skipping oxu.az article outside date range: url={}, postDate={}", card.postUrl(), postDate);
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.OUT_OF_RANGE);
		}

		String text = extractArticleText(document, card);
		if (text == null || text.isBlank()) {
			log.warn(
					"Skipping oxu.az article with empty text: url={}, title={}, selectorsTried={}",
					card.postUrl(),
					card.title(),
					List.of(ARTICLE_ROOT_SELECTORS)
			);
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.EMPTY_TEXT);
		}

		String externalPostId = OxuAzScraperSupport.extractExternalPostId(card.postUrl())
				.orElseGet(() -> Integer.toHexString(card.postUrl().hashCode()));
		List<ScrapedMediaDTO> media = extractMedia(document, card);
		Map<String, Object> metadata = metadata(context, card);

		log.info("Collected oxu.az article: url={}, mediaCount={}", card.postUrl(), media.size());
		return ArticleCollectionAttempt.collected(new ScrapedPostDTO(
				externalPostId,
				card.postUrl(),
				postDate,
				AUTHOR,
				text,
				"az",
				media,
				metadata
		));
	}

	Optional<OffsetDateTime> parseArticleDate(Document document) {
		Optional<String> metaDate = firstMetaContent(
				document,
				"meta[property=article:published_time]",
				"meta[property=og:updated_time]",
				"meta[name=date]"
		);
		if (metaDate.isPresent()) {
			Optional<OffsetDateTime> parsedMetaDate = parseOffsetDateTime(metaDate.get());
			if (parsedMetaDate.isPresent()) {
				return parsedMetaDate;
			}
			Optional<OffsetDateTime> parsedTextDate = dateParser.parseArticleDate(metaDate.get());
			if (parsedTextDate.isPresent()) {
				return parsedTextDate;
			}
		}

		for (Element element : document.select(ARTICLE_DATE_SELECTOR)) {
			String candidate = firstNonBlank(element.attr("datetime"), element.text());
			if (candidate == null || !looksLikeDate(candidate)) {
				continue;
			}
			Optional<OffsetDateTime> parsedOffsetDateTime = parseOffsetDateTime(candidate);
			if (parsedOffsetDateTime.isPresent()) {
				return parsedOffsetDateTime;
			}
			Optional<OffsetDateTime> parsedDate = dateParser.parseArticleDate(candidate);
			if (parsedDate.isPresent()) {
				return parsedDate;
			}
		}
		return Optional.empty();
	}

	String extractArticleText(Document document, OxuAzSearchResultCard card) {
		String bestText = null;
		for (Element root : findArticleRoots(document)) {
			String paragraphText = extractParagraphText(root);
			if (isUsableArticleText(paragraphText)) {
				if (isBetterArticleText(paragraphText, bestText)) {
					bestText = paragraphText;
				}
			}
		}
		if (isUsableArticleText(bestText)) {
			return bestText;
		}
		Optional<String> metaDescription = firstMetaContent(
				document,
				"meta[name=description]",
				"meta[property=description]",
				"meta[property=og:description]"
		).map(this::normalizeArticleText)
				.filter(this::isUsableArticleText);
		if (metaDescription.isPresent()) {
			log.debug("Using oxu.az meta description fallback for article: url={}", card.postUrl());
			return metaDescription.get();
		}
		return null;
	}

	private List<Element> findArticleRoots(Document document) {
		List<Element> roots = new ArrayList<>();
		Set<Element> seen = new LinkedHashSet<>();
		for (String selector : ARTICLE_ROOT_SELECTORS) {
			for (Element root : document.select(selector)) {
				if (seen.add(root)) {
					roots.add(root);
				}
			}
		}
		return roots;
	}

	private String extractParagraphText(Element root) {
		Element cleanRoot = cleanedArticleContent(root);
		List<String> paragraphs = cleanRoot
				.select("p")
				.stream()
				.map(Element::text)
				.map(this::normalizeArticleText)
				.filter(this::isArticleParagraph)
				.toList();
		String text = String.join("\n", paragraphs);
		return isUsableArticleText(text) ? text : null;
	}

	private Element cleanedArticleContent(Element root) {
		Element cleanRoot = root.clone();
		cleanRoot.select(CLEANUP_SELECTOR).remove();
		cleanRoot.select("*")
				.stream()
				.filter(this::isAdOnlyElement)
				.toList()
				.forEach(Element::remove);
		return cleanRoot;
	}

	private boolean isAdOnlyElement(Element element) {
		String text = normalizeArticleText(element.ownText());
		return text != null && "ad".equalsIgnoreCase(text);
	}

	private boolean isArticleParagraph(String text) {
		if (text == null || text.length() < MIN_PARAGRAPH_TEXT_LENGTH) {
			return false;
		}
		String normalized = normalizeForNoiseChecks(text);
		return !normalized.equals("dili secin")
				&& !normalized.equals("son xeberler")
				&& !normalized.equals("butun xeberler")
				&& !normalized.equals("reklam")
				&& !containsAdvertisingNoise(normalized);
	}

	private boolean containsAdvertisingNoise(String normalizedText) {
		return normalizedText.contains("yandex")
				|| normalizedText.contains("doubleclick")
				|| normalizedText.contains("googleads")
				|| normalizedText.contains("reklam")
				|| normalizedText.contains("banner")
				|| normalizedText.contains("advert")
				|| normalizedText.contains("sosial sebekelerde paylasin")
				|| normalizedText.contains("en son xeberleri bizim")
				|| normalizedText.contains("whatsapp kanali")
				|| normalizedText.contains("telegram kanali")
				|| normalizedText.contains("facebook sehifemizde");
	}

	private boolean isBetterArticleText(String candidate, String current) {
		return isUsableArticleText(candidate) && (current == null || candidate.length() > current.length());
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

	List<ScrapedMediaDTO> extractMedia(Document document, OxuAzSearchResultCard card) {
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		extractMetaImageUrl(document, card.postUrl())
				.or(() -> extractMainImageUrl(document, card.postUrl()))
				.ifPresent(mediaUrl -> mediaByUrl.put(mediaUrl, MediaType.IMAGE));
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

	private Optional<String> extractMetaImageUrl(Document document, String baseUrl) {
		return firstMetaContent(
				document,
				"meta[property=og:image]",
				"meta[name=twitter:image]"
		).map(rawUrl -> UrlNormalizer.resolve(baseUrl, rawUrl))
				.filter(OxuAzScraperSupport::isAllowedMediaUrl);
	}

	private Optional<String> extractMainImageUrl(Document document, String baseUrl) {
		for (Element root : findArticleRoots(document)) {
			Element cleanRoot = cleanedArticleContent(root);
			Element image = cleanRoot.selectFirst("img[src], img[data-src], img[srcset], img[data-srcset]");
			if (image == null) {
				continue;
			}
			String rawUrl = firstNonBlank(
					image.attr("src"),
					image.attr("data-src"),
					firstSrcsetUrl(image.attr("srcset")),
					firstSrcsetUrl(image.attr("data-srcset"))
			);
			if (rawUrl == null || !OxuAzScraperSupport.isAllowedMediaUrl(rawUrl)) {
				continue;
			}
			String normalizedUrl = UrlNormalizer.resolve(baseUrl, rawUrl);
			if (OxuAzScraperSupport.isAllowedMediaUrl(normalizedUrl)) {
				return Optional.of(normalizedUrl);
			}
		}
		return Optional.empty();
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

	private Map<String, Object> metadata(ScraperExecutionContext context, OxuAzSearchResultCard card) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", OxuAzScraperSupport.SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		if (card.title() != null && !card.title().isBlank()) {
			metadata.put("title", card.title());
		}
		if (card.thumbnailUrl() != null && !card.thumbnailUrl().isBlank()) {
			metadata.put("thumbnailUrl", card.thumbnailUrl());
		}
		return metadata;
	}

	private boolean hasResultUrl(Element element) {
		return firstNonBlank(
				element.attr("data-url"),
				attr(element, "h2.post-item-title a[href]", "href"),
				attr(element, ".post-item-img a[href]", "href")
		) != null;
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

	private Optional<OffsetDateTime> parseOffsetDateTime(String value) {
		try {
			return Optional.of(OffsetDateTime.parse(value.trim()));
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
	}

	private boolean looksLikeDate(String value) {
		String normalized = normalizeForNoiseChecks(value);
		return normalized.matches(".*\\d{1,2}:\\d{2}.*")
				|| normalized.matches(".*\\d{4}-\\d{2}-\\d{2}.*")
				|| normalized.contains("bugun")
				|| normalized.contains("bu gun")
				|| normalized.contains("dunen");
	}

	private String normalizeForNoiseChecks(String value) {
		return value.toLowerCase(Locale.ROOT)
				.replace("ü", "u")
				.replace("ə", "e")
				.replace("ı", "i")
				.replace("ğ", "g")
				.replace("ş", "s")
				.replace("ç", "c")
				.replace("ö", "o")
				.replace("Ã¼", "u")
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

	private record OxuAzArticleCollectionResult(
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
			String message = ("oxu.az extraction failed: candidatesFound=%d, articlesOpened=%d, "
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
