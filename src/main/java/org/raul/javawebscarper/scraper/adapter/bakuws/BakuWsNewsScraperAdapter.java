package org.raul.javawebscarper.scraper.adapter.bakuws;

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
public class BakuWsNewsScraperAdapter implements NewsScraperAdapter {

	private static final int MIN_ARTICLE_TEXT_LENGTH = 50;
	private static final int MIN_PARAGRAPH_TEXT_LENGTH = 20;
	private static final String[] ARTICLE_ROOT_SELECTORS = {
			".post-detail.post-detail-area",
			".post-detail",
			"section.news-detail",
			".news-detail",
			"main article",
			"article",
			".article-content",
			".single-news-content",
			".news-content",
			".post-content",
			".post-text",
			".entry-content",
			".news-inner",
			".post-detail-area",
			"[class*='article']",
			"[class*='news']",
			"[class*='content']"
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
			".cat-left-bnr",
			".side-bnr",
			".bnr-is-post",
			".AdviadNativeVideo",
			".social-media-banner",
			".tag-area",
			"[data-ad-id]",
			"[data-name=adWrapper]",
			"[href*=yandex]",
			"[src*=yandex]",
			"[href*='avatars.mds']",
			"[src*='avatars.mds']",
			"[id^=yandex_rtb]",
			"[id*=yandex]",
			"[class*=yandex]",
			"[id*=bnr]",
			"[class*=bnr]",
			"[class*=banner]",
			"[class*=advert]",
			"[class*=sidebar]",
			"[class*=comment]",
			".post-date",
			".post-detail-title",
			".post-detail-img",
			".similar-news",
			".related-news"
	);
	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"baku.ws",
			"baku.ws",
			"Baku.ws",
			BakuWsScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final BakuWsDateParser dateParser;

	@Override
	public String sourceCode() {
		return BakuWsScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return BakuWsScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < 3) {
			return ScraperExecutionResult.failed("baku.ws search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		log.info(
				"Starting baku.ws scraping: keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
				keyword,
				context.dateFrom(),
				context.dateTo(),
				context.maxPages(),
				context.maxPosts()
		);

		try (BrowserSession session = browserSessionFactory.createSession()) {
			BrowserPage page = session.newPage();
			openSearchPage(page, keyword.trim());
			List<BakuWsSearchResultCard> cards = collectSearchResultCards(page, context);
			BakuWsArticleCollectionResult result = collectArticles(page, cards, context);
			log.info(
					"Finished baku.ws scraping: keyword={}, candidatesFound={}, articlesOpened={}, "
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
			log.warn("baku.ws scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("baku.ws scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected baku.ws scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected baku.ws scraping failure: " + exception.getMessage());
		}
	}

	private void openSearchPage(BrowserPage page, String keyword) {
		String fallbackUrl = searchUrl(keyword);
		try {
			log.info("Opening baku.ws search through UI flow: keyword={}", keyword);
			page.navigate(BakuWsScraperSupport.BASE_URL);
			clickSearchToggle(page);
			waitForSearchForm(page);
			String searchInputSelector = waitForSearchInput(page);
			page.fill(searchInputSelector, keyword);
			page.press(searchInputSelector, "Enter");
			page.waitForTimeout(1_000);
			if (!page.url().contains("/search")) {
				log.warn("baku.ws UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			}
		} catch (BrowserEngineException exception) {
			log.warn("baku.ws UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
		}
		log.info("baku.ws search page opened: {}", page.url());
	}

	private void clickSearchToggle(BrowserPage page) {
		BrowserEngineException lastException = null;
		for (String selector : BakuWsSelectors.SEARCH_TOGGLE_SELECTORS) {
			try {
				page.waitForSelector(selector, 3_000);
				page.click(selector);
				return;
			} catch (BrowserEngineException exception) {
				lastException = exception;
				log.debug("baku.ws search toggle selector did not work: selector={}", selector);
			}
		}
		throw new BrowserEngineException("baku.ws search toggle was not found", lastException);
	}

	private void waitForSearchForm(BrowserPage page) {
		try {
			page.waitForSelector(BakuWsSelectors.SEARCH_FORM_OPEN, 5_000);
		} catch (BrowserEngineException exception) {
			page.waitForSelector(BakuWsSelectors.SEARCH_FORM, 5_000);
		}
	}

	private String waitForSearchInput(BrowserPage page) {
		try {
			page.waitForSelector(BakuWsSelectors.SEARCH_INPUT, 5_000);
			return BakuWsSelectors.SEARCH_INPUT;
		} catch (BrowserEngineException exception) {
			page.waitForSelector(BakuWsSelectors.SEARCH_INPUT_FALLBACK, 5_000);
			return BakuWsSelectors.SEARCH_INPUT_FALLBACK;
		}
	}

	String searchUrl(String keyword) {
		return BakuWsScraperSupport.BASE_URL + "search?query="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8);
	}

	private List<BakuWsSearchResultCard> collectSearchResultCards(BrowserPage page, ScraperExecutionContext context) {
		Map<String, BakuWsSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();

		for (int scroll = 0; scroll < context.maxPages() && cardsByUrl.size() < context.maxPosts(); scroll++) {
			CardCollectionStats stats = collectCurrentCards(page, context, cardsByUrl, seenUrls, scroll + 1);
			log.info(
					"baku.ws search cards pass={}: found={}, added={}, duplicateCards={}, "
							+ "skippedAds={}, skippedMissingUrl={}, skippedDateParse={}, beforeRange={}, afterRange={}",
					scroll + 1,
					stats.found(),
					stats.added(),
					stats.duplicates(),
					stats.skippedAds(),
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
			log.info(
					"baku.ws candidate URLs: {}",
					cardsByUrl.keySet().stream().limit(10).toList()
			);
		}
		return cardsByUrl.values().stream().limit(context.maxPosts()).toList();
	}

	private CardCollectionStats collectCurrentCards(
			BrowserPage page,
			ScraperExecutionContext context,
			Map<String, BakuWsSearchResultCard> cardsByUrl,
			Set<String> seenUrls,
			int scrollBatch
	) {
		Document document = Jsoup.parse(page.content(), BakuWsScraperSupport.BASE_URL);
		Elements elements = document.select(BakuWsSelectors.RESULT_CARD);
		if (elements.isEmpty()) {
			elements = document.select(BakuWsSelectors.RESULT_CARD_FALLBACK);
		}
		int found = elements.size();
		int added = 0;
		int duplicates = 0;
		int beforeRange = 0;
		int afterRange = 0;
		int skippedAds = 0;
		int skippedMissingUrl = 0;
		int skippedDateParse = 0;
		int newDatedCards = 0;

		for (Element element : elements) {
			CardParseResult parsedCard = parseResultCard(element, scrollBatch);
			if (parsedCard.card() == null) {
				switch (parsedCard.skipReason()) {
					case AD -> skippedAds++;
					case MISSING_URL -> skippedMissingUrl++;
					case DATE_PARSE -> skippedDateParse++;
					default -> {
					}
				}
				continue;
			}
			BakuWsSearchResultCard card = parsedCard.card();
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
				skippedAds,
				skippedMissingUrl,
				skippedDateParse,
				newDatedCards
		);
	}

	CardParseResult parseResultCard(Element element, int scrollBatch) {
		if (isAdCard(element)) {
			return CardParseResult.skipped(CardSkipReason.AD);
		}
		String postUrl = firstNonBlank(
				element.selectFirst(BakuWsSelectors.RESULT_CARD_DATA_URL) == null
						? null
						: element.selectFirst(BakuWsSelectors.RESULT_CARD_DATA_URL).attr("data-url"),
				element.selectFirst(BakuWsSelectors.RESULT_CARD_TITLE_LINK) == null
						? null
						: element.selectFirst(BakuWsSelectors.RESULT_CARD_TITLE_LINK).attr("href"),
				element.selectFirst(BakuWsSelectors.RESULT_CARD_IMAGE_LINK) == null
						? null
						: element.selectFirst(BakuWsSelectors.RESULT_CARD_IMAGE_LINK).attr("href")
		);
		if (postUrl == null) {
			log.warn("Skipping baku.ws card without post URL");
			return CardParseResult.skipped(CardSkipReason.MISSING_URL);
		}
		String normalizedUrl = BakuWsScraperSupport.normalizePostUrl(postUrl);
		if (normalizedUrl == null) {
			log.warn("Skipping baku.ws card with invalid post URL: rawUrl={}", postUrl);
			return CardParseResult.skipped(CardSkipReason.MISSING_URL);
		}
		String time = text(element, BakuWsSelectors.RESULT_CARD_TIME);
		String day = text(element, BakuWsSelectors.RESULT_CARD_DAY);
		Optional<OffsetDateTime> postDate = dateParser.parseResultCardDate(day, time);
		if (postDate.isEmpty()) {
			log.warn("Skipping baku.ws card with unparseable date: url={}, day={}, time={}", normalizedUrl, day, time);
			return CardParseResult.skipped(CardSkipReason.DATE_PARSE);
		}
		String thumbnailUrl = Optional.ofNullable(element.selectFirst(BakuWsSelectors.RESULT_CARD_THUMBNAIL))
				.map(image -> firstNonBlank(image.attr("src"), image.attr("data-src")))
				.filter(value -> !value.isBlank())
				.map(value -> UrlNormalizer.resolve(BakuWsScraperSupport.BASE_URL, value))
				.filter(BakuWsScraperSupport::isAllowedMediaUrl)
				.orElse(null);

		return CardParseResult.card(new BakuWsSearchResultCard(
				normalizedUrl,
				text(element, BakuWsSelectors.RESULT_CARD_TITLE),
				postDate.get(),
				thumbnailUrl,
				scrollBatch
		));
	}

	private boolean isAdCard(Element element) {
		if (element == null) {
			return false;
		}
		return element.is(BakuWsSelectors.SEARCH_AD_BLOCK)
				|| element.parents().stream().anyMatch(parent -> parent.is(BakuWsSelectors.SEARCH_AD_BLOCK))
				|| element.selectFirst("[class*=banner], img[src*='/images/banners/'], img[src*=placeholder_home]") != null;
	}

	private BakuWsArticleCollectionResult collectArticles(
			BrowserPage page,
			List<BakuWsSearchResultCard> cards,
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

		for (BakuWsSearchResultCard card : cards) {
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
				if (seenExternalIds.add(attempt.post().externalPostId()) && seenPostUrls.add(attempt.post().postUrl())) {
					posts.add(attempt.post());
				} else {
					duplicates++;
					log.debug(
							"Skipping duplicate baku.ws article: externalPostId={}, postUrl={}",
							attempt.post().externalPostId(),
							attempt.post().postUrl()
					);
				}
			} catch (RuntimeException exception) {
				skippedErrors++;
				log.warn("Skipping baku.ws article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return new BakuWsArticleCollectionResult(
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
			BakuWsSearchResultCard card,
			ScraperExecutionContext context
	) {
		try {
			return collectArticle(page, card, context);
		} catch (BrowserTimeoutException exception) {
			log.warn("Timeout opening baku.ws article, retrying once: url={}", card.postUrl());
			return collectArticle(page, card, context);
		}
	}

	private ArticleCollectionAttempt collectArticle(
			BrowserPage page,
			BakuWsSearchResultCard card,
			ScraperExecutionContext context
	) {
		page.navigate(card.postUrl());
		page.waitForTimeout(500);
		Document document = Jsoup.parse(page.content(), card.postUrl());
		OffsetDateTime postDate = parseArticleDate(document).orElse(card.postDate());
		if (!DateRangeValidator.isInsideRange(postDate, context.dateFrom(), context.dateTo())) {
			log.debug("Skipping baku.ws article outside date range: url={}, postDate={}", card.postUrl(), postDate);
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.OUT_OF_RANGE);
		}

		String text = extractArticleText(document, card);
		if (text == null || text.isBlank()) {
			log.warn(
					"Skipping baku.ws article with empty text: url={}, title={}, selectorsTried={}",
					card.postUrl(),
					card.title(),
					List.of(ARTICLE_ROOT_SELECTORS)
			);
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.EMPTY_TEXT);
		}

		String dataPage = Optional.ofNullable(document.selectFirst(BakuWsSelectors.ARTICLE_DATA_PAGE))
				.map(element -> firstNonBlank(element.attr("data-page")))
				.orElse(null);
		String externalPostId = BakuWsScraperSupport.extractExternalPostId(dataPage, card.postUrl())
				.orElseGet(() -> Integer.toHexString(card.postUrl().hashCode()));
		List<ScrapedMediaDTO> media = extractMedia(document, card);
		Map<String, Object> metadata = metadata(context, card);

		log.info("Collected baku.ws article: url={}, mediaCount={}", card.postUrl(), media.size());
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
		String day = text(document, BakuWsSelectors.ARTICLE_DATE_DAY);
		String month = text(document, BakuWsSelectors.ARTICLE_DATE_MONTH);
		String year = text(document, BakuWsSelectors.ARTICLE_DATE_YEAR);
		String time = text(document, BakuWsSelectors.ARTICLE_DATE_TIME);
		if (day != null && month != null && year != null && time != null) {
			Optional<OffsetDateTime> parsedDate = dateParser.parseArticleDate(day, month, year, time);
			if (parsedDate.isPresent()) {
				return parsedDate;
			}
		}
		return Optional.empty();
	}

	String extractArticleText(Document document, BakuWsSearchResultCard card) {
		String bestText = null;
		for (Element root : findArticleTextRoots(document)) {
			String paragraphText = extractParagraphText(root);
			if (isUsableArticleText(paragraphText)) {
				if (isBetterArticleText(paragraphText, bestText)) {
					bestText = paragraphText;
				}
				continue;
			}
			String rootText = extractRootText(root);
			if (isBetterArticleText(rootText, bestText)) {
				bestText = rootText;
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
			log.debug("Using baku.ws meta description fallback for article: url={}", card.postUrl());
			return metaDescription.get();
		}
		return null;
	}

	private List<Element> findArticleTextRoots(Document document) {
		List<Element> roots = new ArrayList<>();
		Set<Element> seen = new LinkedHashSet<>();
		for (Element root : document.select(BakuWsSelectors.ARTICLE_TEXT_CONTENT)) {
			if (seen.add(root)) {
				roots.add(root);
			}
		}
		return roots;
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

	private String extractRootText(Element root) {
		Element cleanElement = cleanedArticleContent(root);
		String text = normalizeArticleText(cleanElement.text());
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
		String normalized = text.toLowerCase(Locale.ROOT)
				.replace("ə", "e")
				.replace("ü", "u");
		return !normalized.equals("dili secin")
				&& !normalized.equals("son xeberler")
				&& !normalized.equals("butun xeberler")
				&& !containsAdvertisingNoise(normalized);
	}

	private boolean containsAdvertisingNoise(String normalizedText) {
		return normalizedText.contains("yandex")
				|| normalizedText.contains("avatars.mds")
				|| normalizedText.contains("colizeum")
				|| normalizedText.contains("reklam")
				|| normalizedText.contains("banner");
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

	List<ScrapedMediaDTO> extractMedia(Document document, BakuWsSearchResultCard card) {
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		extractMainImageUrl(document, card.postUrl())
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

	private Optional<String> extractMainImageUrl(Document document, String baseUrl) {
		Element image = document.selectFirst(BakuWsSelectors.ARTICLE_MAIN_IMAGE);
		if (image == null) {
			return Optional.empty();
		}
		String rawUrl = firstNonBlank(
				image.attr("src"),
				image.attr("data-src"),
				firstSrcsetUrl(image.attr("srcset")),
				firstSrcsetUrl(image.attr("data-srcset"))
		);
		if (rawUrl == null || !BakuWsScraperSupport.isAllowedMediaUrl(rawUrl)) {
			return Optional.empty();
		}
		String normalizedUrl = UrlNormalizer.resolve(baseUrl, rawUrl);
		return BakuWsScraperSupport.isAllowedMediaUrl(normalizedUrl)
				? Optional.of(normalizedUrl)
				: Optional.empty();
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

	private Map<String, Object> metadata(ScraperExecutionContext context, BakuWsSearchResultCard card) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", BakuWsScraperSupport.SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		if (card.title() != null && !card.title().isBlank()) {
			metadata.put("title", card.title());
		}
		if (card.thumbnailUrl() != null && !card.thumbnailUrl().isBlank()) {
			metadata.put("thumbnailUrl", card.thumbnailUrl());
		}
		metadata.put("scrollBatch", card.scrollBatch());
		return metadata;
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
			log.warn("Unable to parse baku.ws article meta date: value={}", value);
			return Optional.empty();
		}
	}

	enum CardSkipReason {
		AD,
		MISSING_URL,
		DATE_PARSE
	}

	private enum ArticleSkipReason {
		EMPTY_TEXT,
		OUT_OF_RANGE
	}

	record CardParseResult(
			BakuWsSearchResultCard card,
			CardSkipReason skipReason
	) {

		private static CardParseResult card(BakuWsSearchResultCard card) {
			return new CardParseResult(card, null);
		}

		private static CardParseResult skipped(CardSkipReason reason) {
			return new CardParseResult(null, reason);
		}
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

	private record BakuWsArticleCollectionResult(
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
			String message = ("baku.ws extraction failed: candidatesFound=%d, articlesOpened=%d, "
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
			int skippedAds,
			int skippedMissingUrl,
			int skippedDateParse,
			int newDatedCards
	) {

		private boolean onlyNewCardsAreOld() {
			return newDatedCards > 0 && beforeRange == newDatedCards;
		}
	}
}
