package org.raul.javawebscarper.scraper.adapter.haqqinaz;

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
public class HaqqinAzNewsScraperAdapter implements NewsScraperAdapter {

	private static final int MIN_KEYWORD_LENGTH = 3;
	private static final int MIN_ARTICLE_TEXT_LENGTH = 50;
	private static final int MIN_PARAGRAPH_TEXT_LENGTH = 20;
	private static final int MAX_LOAD_MORE_ATTEMPTS = 10;
	private static final String ARTICLE_CLEANUP_SELECTOR = String.join(", ",
			".ad-block",
			".ad-block__wrapper",
			".ad-block__item",
			".block-subscribe",
			".subscribe-item",
			"iframe",
			"script",
			"style",
			"template",
			"ins",
			"[class*=banner]",
			"[id*=banner]"
	);
	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"haqqin.az",
			"haqqin.az",
			"Haqqin.az",
			HaqqinAzScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final HaqqinAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return HaqqinAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return HaqqinAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("haqqin.az search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		log.info(
				"Starting haqqin.az scraping: source={}, keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
				context.source().getCode(),
				keyword,
				context.dateFrom(),
				context.dateTo(),
				context.maxPages(),
				context.maxPosts()
		);

		try (BrowserSession session = browserSessionFactory.createSession()) {
			BrowserPage page = session.newPage();
			SearchCollectionResult searchResult = openAndCollectSearchResults(page, keyword.trim(), context);
			if (searchResult.failureMessage() != null) {
				return ScraperExecutionResult.failed(searchResult.failureMessage());
			}
			log.info(
					"Finished haqqin.az search collection: searchCardsFound={}, acceptedUrls={}, "
							+ "duplicatesSkipped={}, invalidUrlsSkipped={}, loadMoreClicks={}, batchesProcessed={}, "
							+ "dateParseFailures={}, tooNewSkipped={}, tooOldSkipped={}",
					searchResult.stats().searchCardsFound(),
					searchResult.cards().size(),
					searchResult.stats().duplicatesSkipped(),
					searchResult.stats().invalidUrlsSkipped(),
					searchResult.stats().loadMoreClicks(),
					searchResult.stats().batchesProcessed(),
					searchResult.stats().dateParseFailures(),
					searchResult.stats().tooNewSkipped(),
					searchResult.stats().tooOldSkipped()
			);
			ArticleCollectionResult articleResult = collectArticles(page, searchResult.cards(), context);
			log.info(
					"Finished haqqin.az scraping: searchCardsFound={}, acceptedUrls={}, duplicatesSkipped={}, "
							+ "invalidUrlsSkipped={}, loadMoreClicks={}, batchesProcessed={}, articlePagesOpened={}, "
							+ "postsFound={}, dateParseFailures={}, tooNewSkipped={}, tooOldSkipped={}, "
							+ "emptyTextSkipped={}, articleTimeouts={}, mediaCollected={}",
					searchResult.stats().searchCardsFound(),
					searchResult.cards().size(),
					searchResult.stats().duplicatesSkipped() + articleResult.duplicates(),
					searchResult.stats().invalidUrlsSkipped(),
					searchResult.stats().loadMoreClicks(),
					searchResult.stats().batchesProcessed(),
					articleResult.articlePagesOpened(),
					articleResult.posts().size(),
					searchResult.stats().dateParseFailures() + articleResult.dateParseFailures(),
					searchResult.stats().tooNewSkipped() + articleResult.tooNewSkipped(),
					searchResult.stats().tooOldSkipped() + articleResult.tooOldSkipped(),
					articleResult.emptyTextSkipped(),
					articleResult.articleTimeouts(),
					articleResult.mediaCollected()
			);
			if (!articleResult.posts().isEmpty()) {
				return ScraperExecutionResult.success(articleResult.posts());
			}
			if (searchResult.cards().isEmpty()) {
				return ScraperExecutionResult.empty();
			}
			if (articleResult.hasExtractionFailures()) {
				return articleResult.toFailedScraperResult(searchResult.cards().size());
			}
			return ScraperExecutionResult.empty();
		} catch (BrowserEngineException exception) {
			log.warn("haqqin.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("haqqin.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected haqqin.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected haqqin.az scraping failure: " + exception.getMessage());
		}
	}

	private ArticleCollectionResult collectArticles(
			BrowserPage page,
			List<HaqqinAzSearchResultCard> cards,
			ScraperExecutionContext context
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenExternalIds = new LinkedHashSet<>();
		Set<String> seenPostUrls = new LinkedHashSet<>();
		int articlePagesOpened = 0;
		int dateParseFailures = 0;
		int tooNewSkipped = 0;
		int tooOldSkipped = 0;
		int emptyTextSkipped = 0;
		int articleErrors = 0;
		int articleTimeouts = 0;
		int duplicates = 0;
		int mediaCollected = 0;

		for (HaqqinAzSearchResultCard card : cards) {
			if (posts.size() >= context.maxPosts()) {
				break;
			}
			articlePagesOpened++;
			try {
				ArticleCollectionAttempt attempt = collectArticleWithRetry(page, card, context);
				if (attempt.post() == null) {
					if (attempt.skipReason() == ArticleSkipReason.INVALID_DATE) {
						dateParseFailures++;
					} else if (attempt.skipReason() == ArticleSkipReason.TOO_NEW) {
						tooNewSkipped++;
					} else if (attempt.skipReason() == ArticleSkipReason.TOO_OLD) {
						tooOldSkipped++;
					} else if (attempt.skipReason() == ArticleSkipReason.EMPTY_TEXT) {
						emptyTextSkipped++;
					}
					continue;
				}
				ScrapedPostDTO post = attempt.post();
				if (seenExternalIds.add(post.externalPostId()) && seenPostUrls.add(post.postUrl())) {
					mediaCollected += post.media().size();
					posts.add(post);
				} else {
					duplicates++;
					log.debug("Skipping duplicate haqqin.az article: url={}, externalPostId={}", post.postUrl(), post.externalPostId());
				}
			} catch (BrowserTimeoutException exception) {
				articleTimeouts++;
				log.warn("Skipping haqqin.az article after timeout: url={}", card.postUrl());
			} catch (RuntimeException exception) {
				articleErrors++;
				log.warn("Skipping haqqin.az article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return new ArticleCollectionResult(
				posts,
				articlePagesOpened,
				dateParseFailures,
				tooNewSkipped,
				tooOldSkipped,
				emptyTextSkipped,
				articleErrors,
				articleTimeouts,
				duplicates,
				mediaCollected
		);
	}

	private ArticleCollectionAttempt collectArticleWithRetry(
			BrowserPage page,
			HaqqinAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		try {
			return collectArticle(page, card, context);
		} catch (BrowserTimeoutException exception) {
			log.warn("Timeout opening haqqin.az article, retrying once: url={}", card.postUrl());
			return collectArticle(page, card, context);
		}
	}

	private ArticleCollectionAttempt collectArticle(
			BrowserPage page,
			HaqqinAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		page.navigate(card.postUrl());
		page.waitForTimeout(500);
		Document document = Jsoup.parse(page.content(), card.postUrl());
		Optional<OffsetDateTime> parsedPostDate = parseArticleDate(document, card);
		if (parsedPostDate.isEmpty()) {
			log.warn("Skipping haqqin.az article with missing or invalid date: url={}", card.postUrl());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.INVALID_DATE);
		}
		OffsetDateTime postDate = parsedPostDate.get();
		if (DateRangeValidator.isAfterRange(postDate, context.dateTo())) {
			log.debug("Skipping haqqin.az article newer than date range: url={}, postDate={}", card.postUrl(), postDate);
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.TOO_NEW);
		}
		if (DateRangeValidator.isBeforeRange(postDate, context.dateFrom())) {
			log.debug("Skipping haqqin.az article older than date range: url={}, postDate={}", card.postUrl(), postDate);
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.TOO_OLD);
		}

		String text = extractArticleText(document);
		if (text == null || text.isBlank()) {
			log.warn("Skipping haqqin.az article with empty text: url={}, title={}", card.postUrl(), card.title());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.EMPTY_TEXT);
		}

		String externalPostId = HaqqinAzScraperSupport.extractExternalPostId(card.postUrl())
				.orElseGet(() -> Integer.toHexString(card.postUrl().hashCode()));
		String title = firstNonBlank(
				text(document, HaqqinAzSelectors.ARTICLE_ROOT + " " + HaqqinAzSelectors.ARTICLE_TITLE),
				text(document, HaqqinAzSelectors.ARTICLE_ROOT + " " + HaqqinAzSelectors.ARTICLE_TITLE_FALLBACK),
				card.title()
		);
		List<ScrapedMediaDTO> media = extractMedia(document, card);
		Map<String, Object> metadata = metadata(context, card, title);

		log.info("Collected haqqin.az article: url={}, mediaCount={}", card.postUrl(), media.size());
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

	Optional<OffsetDateTime> parseArticleDate(Document document, HaqqinAzSearchResultCard card) {
		Element root = document.selectFirst(HaqqinAzSelectors.ARTICLE_ROOT);
		if (root == null) {
			return Optional.ofNullable(card.searchDate());
		}
		for (Element element : root.select(HaqqinAzSelectors.ARTICLE_DATE)) {
			Optional<OffsetDateTime> parsedDate = dateParser.parseArticleDate(element.text(), card.postUrl());
			if (parsedDate.isPresent()) {
				return parsedDate;
			}
		}
		return Optional.ofNullable(card.searchDate());
	}

	String extractArticleText(Document document) {
		Element root = document.selectFirst(HaqqinAzSelectors.ARTICLE_ROOT);
		if (root == null) {
			return null;
		}
		Element content = root.selectFirst(HaqqinAzSelectors.ARTICLE_CONTENT);
		if (content == null) {
			return null;
		}
		Element cleanContent = cleanedArticleContent(content);
		Elements paragraphs = cleanContent.select(".article-block .block-text p");
		if (paragraphs.isEmpty()) {
			paragraphs = cleanContent.select(".block-text p");
		}
		String text = paragraphs.stream()
				.map(Element::text)
				.map(this::normalizeArticleText)
				.filter(this::isArticleParagraph)
				.reduce((left, right) -> left + "\n" + right)
				.orElse(null);
		return isUsableArticleText(text) ? text : null;
	}

	private Element cleanedArticleContent(Element content) {
		Element cleanContent = content.clone();
		cleanContent.select(ARTICLE_CLEANUP_SELECTOR).remove();
		return cleanContent;
	}

	private boolean isArticleParagraph(String text) {
		if (text == null || text.length() < MIN_PARAGRAPH_TEXT_LENGTH) {
			return false;
		}
		String normalized = text.toLowerCase(Locale.ROOT);
		return !normalized.contains("banners.haqqin.az")
				&& !normalized.contains("подписывайтесь на наш канал")
				&& !normalized.contains("iframe")
				&& !normalized.contains("telegram")
				&& !normalized.contains("whatsapp")
				&& !normalized.contains("youtube")
				&& !normalized.contains("реклама");
	}

	private boolean isUsableArticleText(String text) {
		return text != null && text.length() >= MIN_ARTICLE_TEXT_LENGTH;
	}

	List<ScrapedMediaDTO> extractMedia(Document document, HaqqinAzSearchResultCard card) {
		Element root = document.selectFirst(HaqqinAzSelectors.ARTICLE_ROOT);
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		if (root != null) {
			for (Element image : root.select(HaqqinAzSelectors.ARTICLE_MEDIA)) {
				String rawUrl = firstNonBlank(
						image.attr("src"),
						image.attr("data-src"),
						firstSrcsetUrl(image.attr("srcset")),
						firstSrcsetUrl(image.attr("data-srcset"))
				);
				if (rawUrl == null || !HaqqinAzScraperSupport.isAllowedMediaUrl(rawUrl)) {
					continue;
				}
				String normalizedUrl = UrlNormalizer.resolve(card.postUrl(), rawUrl);
				if (HaqqinAzScraperSupport.isAllowedMediaUrl(normalizedUrl)) {
					mediaByUrl.putIfAbsent(normalizedUrl, MediaType.IMAGE);
				}
			}
		}
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

	private Map<String, Object> metadata(ScraperExecutionContext context, HaqqinAzSearchResultCard card, String title) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", HaqqinAzScraperSupport.SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		metadata.put("searchBatch", card.searchBatch());
		if (title != null && !title.isBlank()) {
			metadata.put("title", title);
		}
		return metadata;
	}

	private SearchCollectionResult openAndCollectSearchResults(
			BrowserPage page,
			String keyword,
			ScraperExecutionContext context
	) {
		if (!openSearchPage(page, keyword)) {
			String message = "haqqin.az search page did not load";
			log.warn(message);
			return SearchCollectionResult.failed(message);
		}
		SearchCardsResult cardsResult = collectSearchResultCards(page, context);
		return SearchCollectionResult.success(cardsResult.cards(), cardsResult.stats());
	}

	private boolean openSearchPage(BrowserPage page, String keyword) {
		String fallbackUrl = searchUrl(keyword);
		try {
			log.info("Opening haqqin.az search through UI flow: keyword={}", keyword);
			page.navigate(HaqqinAzScraperSupport.BASE_URL);
			page.waitForSelector(HaqqinAzSelectors.SEARCH_OPEN_BUTTON, 5_000);
			page.click(HaqqinAzSelectors.SEARCH_OPEN_BUTTON);
			page.waitForSelector(HaqqinAzSelectors.SEARCH_INPUT, 5_000);
			page.fill(HaqqinAzSelectors.SEARCH_INPUT, keyword);
			page.press(HaqqinAzSelectors.SEARCH_INPUT, "Enter");
			page.waitForTimeout(1_000);
			if (!isSearchUrl(page.url())) {
				log.warn("haqqin.az UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			}
		} catch (BrowserEngineException exception) {
			log.warn("haqqin.az UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
		}
		boolean loaded = waitForSearchPage(page);
		log.info("haqqin.az search page opened: url={}, loaded={}", page.url(), loaded);
		return loaded;
	}

	private boolean waitForSearchPage(BrowserPage page) {
		try {
			page.waitForSelector("body", 10_000);
			try {
				page.waitForSelector(HaqqinAzSelectors.SEARCH_RESULT_CARD, 5_000);
			} catch (BrowserTimeoutException exception) {
				log.debug("haqqin.az search result cards did not appear before timeout: url={}", page.url());
			}
			return isSearchUrl(page.url());
		} catch (BrowserTimeoutException exception) {
			return false;
		}
	}

	String searchUrl(String keyword) {
		return HaqqinAzScraperSupport.BASE_URL + "search/?q="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8);
	}

	SearchCardsResult collectSearchResultCards(
			BrowserPage page,
			ScraperExecutionContext context
	) {
		Map<String, HaqqinAzSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();
		Set<String> visitedNextPageValues = new LinkedHashSet<>();
		SearchCollectionStats totalStats = new SearchCollectionStats();

		for (int batch = 1; batch <= context.maxPages() && cardsByUrl.size() < context.maxPosts(); batch++) {
			Document document = Jsoup.parse(page.content(), HaqqinAzScraperSupport.BASE_URL);
			BatchCollectionStats batchStats = collectCurrentCards(
					document,
					batch,
					context,
					cardsByUrl,
					seenUrls,
					totalStats
			);
			totalStats.incrementBatchesProcessed();
			log.info(
					"haqqin.az search batch={} stats: found={}, added={}, duplicates={}, invalidUrls={}, "
							+ "dateParseFailures={}, tooNew={}, tooOld={}",
					batch,
					batchStats.found(),
					batchStats.added(),
					batchStats.duplicates(),
					batchStats.invalidUrls(),
					batchStats.dateParseFailures(),
					batchStats.tooNew(),
					batchStats.tooOld()
			);

			if (cardsByUrl.size() >= context.maxPosts() || batch >= context.maxPages()) {
				break;
			}
			if (batchStats.onlyNewDatedCardsAreOld()) {
				log.info("Stopping haqqin.az load-more because batch {} is older than dateFrom", batch);
				break;
			}
			Optional<String> nextPageValue = nextPageValue(document);
			if (nextPageValue.isEmpty()) {
				break;
			}
			if (!visitedNextPageValues.add(nextPageValue.get())) {
				log.warn("Stopping haqqin.az load-more because data-next-page repeated: {}", nextPageValue.get());
				break;
			}
			if (!clickLoadMoreAndWaitForNewCards(page, nextPageValue.get(), seenUrls)) {
				break;
			}
			totalStats.incrementLoadMoreClicks();
		}
		if (!cardsByUrl.isEmpty()) {
			log.info("haqqin.az candidate URLs: {}", cardsByUrl.keySet().stream().limit(10).toList());
		}
		return new SearchCardsResult(cardsByUrl.values().stream().limit(context.maxPosts()).toList(), totalStats);
	}

	private BatchCollectionStats collectCurrentCards(
			Document document,
			int batch,
			ScraperExecutionContext context,
			Map<String, HaqqinAzSearchResultCard> cardsByUrl,
			Set<String> seenUrls,
			SearchCollectionStats totalStats
	) {
		Elements elements = document.select(HaqqinAzSelectors.SEARCH_RESULT_CARD);
		BatchCollectionStats stats = new BatchCollectionStats(elements.size());
		totalStats.addSearchCardsFound(elements.size());
		for (Element element : elements) {
			SearchCardParseResult parsedResult = parseResultCard(element, batch);
			if (parsedResult.card() == null) {
				if (parsedResult.skipReason() == SearchCardSkipReason.DATE_PARSE) {
					stats.incrementDateParseFailures();
					totalStats.incrementDateParseFailures();
				} else {
					stats.incrementInvalidUrls();
					totalStats.incrementInvalidUrlsSkipped();
				}
				continue;
			}
			HaqqinAzSearchResultCard card = parsedResult.card();
			if (!seenUrls.add(card.postUrl())) {
				stats.incrementDuplicates();
				totalStats.incrementDuplicatesSkipped();
				continue;
			}
			if (card.searchDate() != null) {
				stats.incrementNewDatedCards();
				if (DateRangeValidator.isAfterRange(card.searchDate(), context.dateTo())) {
					stats.incrementTooNew();
					totalStats.incrementTooNewSkipped();
					continue;
				}
				if (DateRangeValidator.isBeforeRange(card.searchDate(), context.dateFrom())) {
					stats.incrementTooOld();
					totalStats.incrementTooOldSkipped();
					continue;
				}
			}
			cardsByUrl.put(card.postUrl(), card);
			stats.incrementAdded();
			if (cardsByUrl.size() >= context.maxPosts()) {
				break;
			}
		}
		return stats;
	}

	SearchCardParseResult parseResultCard(Element element, int searchBatch) {
		String rawUrl = element.attr("href");
		String normalizedUrl = HaqqinAzScraperSupport.normalizeArticleUrl(rawUrl).orElse(null);
		if (normalizedUrl == null) {
			return SearchCardParseResult.skipped(SearchCardSkipReason.EXTERNAL_OR_INVALID_URL);
		}
		String dateText = text(element, HaqqinAzSelectors.SEARCH_CARD_DATE);
		OffsetDateTime searchDate = null;
		if (dateText != null && !dateText.isBlank()) {
			Optional<OffsetDateTime> parsedDate = dateParser.parseArticleDate(dateText, normalizedUrl);
			if (parsedDate.isEmpty()) {
				return SearchCardParseResult.skipped(SearchCardSkipReason.DATE_PARSE);
			}
			searchDate = parsedDate.get();
		}
		String thumbnailUrl = extractThumbnailUrl(element, normalizedUrl).orElse(null);
		return SearchCardParseResult.card(new HaqqinAzSearchResultCard(
				normalizedUrl,
				text(element, HaqqinAzSelectors.SEARCH_CARD_TITLE),
				searchDate,
				thumbnailUrl,
				searchBatch
		));
	}

	Optional<String> nextPageValue(Document document) {
		Element button = document.selectFirst(HaqqinAzSelectors.LOAD_MORE_BUTTON);
		if (button == null) {
			return Optional.empty();
		}
		if (button.hasClass("disabled") || "true".equalsIgnoreCase(button.attr("disabled"))) {
			return Optional.empty();
		}
		String value = button.attr("data-next-page");
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
	}

	private boolean clickLoadMoreAndWaitForNewCards(BrowserPage page, String previousNextPageValue, Set<String> alreadySeenUrls) {
		Document before = Jsoup.parse(page.content(), HaqqinAzScraperSupport.BASE_URL);
		int previousCount = before.select(HaqqinAzSelectors.SEARCH_RESULT_CARD).size();
		Set<String> previousUrls = collectArticleUrls(before);
		try {
			page.click(HaqqinAzSelectors.LOAD_MORE_BUTTON);
		} catch (BrowserEngineException exception) {
			log.warn("Stopping haqqin.az load-more because click failed: {}", exception.getMessage());
			return false;
		}
		for (int attempt = 0; attempt < MAX_LOAD_MORE_ATTEMPTS; attempt++) {
			page.waitForTimeout(500);
			Document after = Jsoup.parse(page.content(), HaqqinAzScraperSupport.BASE_URL);
			int currentCount = after.select(HaqqinAzSelectors.SEARCH_RESULT_CARD).size();
			Set<String> currentUrls = collectArticleUrls(after);
			boolean hasNewUrl = currentUrls.stream()
					.anyMatch(url -> !previousUrls.contains(url) && !alreadySeenUrls.contains(url));
			boolean countIncreased = currentCount > previousCount;
			boolean nextPageChanged = nextPageValue(after)
					.map(value -> !value.equals(previousNextPageValue))
					.orElse(true);
			if (hasNewUrl || countIncreased || nextPageChanged) {
				return true;
			}
		}
		log.warn("Stopping haqqin.az load-more because no new cards appeared for data-next-page={}", previousNextPageValue);
		return false;
	}

	private Set<String> collectArticleUrls(Document document) {
		Set<String> urls = new LinkedHashSet<>();
		for (Element element : document.select(HaqqinAzSelectors.SEARCH_RESULT_CARD)) {
			HaqqinAzScraperSupport.normalizeArticleUrl(element.attr("href")).ifPresent(urls::add);
		}
		return urls;
	}

	private Optional<String> extractThumbnailUrl(Element element, String baseUrl) {
		Element image = element.selectFirst(HaqqinAzSelectors.SEARCH_CARD_THUMBNAIL);
		if (image == null) {
			return Optional.empty();
		}
		String rawUrl = firstNonBlank(
				image.attr("src"),
				image.attr("data-src"),
				firstSrcsetUrl(image.attr("srcset")),
				firstSrcsetUrl(image.attr("data-srcset"))
		);
		if (rawUrl == null || !HaqqinAzScraperSupport.isAllowedMediaUrl(rawUrl)) {
			return Optional.empty();
		}
		String normalizedUrl = UrlNormalizer.resolve(baseUrl, rawUrl);
		return HaqqinAzScraperSupport.isAllowedMediaUrl(normalizedUrl)
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

	private boolean isSearchUrl(String url) {
		return url != null && url.toLowerCase(Locale.ROOT).contains("/search/");
	}

	private String text(Element element, String selector) {
		Element selected = element.selectFirst(selector);
		return selected == null ? null : normalizeArticleText(selected.text());
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
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

	enum SearchCardSkipReason {
		EXTERNAL_OR_INVALID_URL,
		DATE_PARSE
	}

	private enum ArticleSkipReason {
		INVALID_DATE,
		TOO_NEW,
		TOO_OLD,
		EMPTY_TEXT
	}

	record SearchCardParseResult(
			HaqqinAzSearchResultCard card,
			SearchCardSkipReason skipReason
	) {

		static SearchCardParseResult card(HaqqinAzSearchResultCard card) {
			return new SearchCardParseResult(card, null);
		}

		static SearchCardParseResult skipped(SearchCardSkipReason reason) {
			return new SearchCardParseResult(null, reason);
		}
	}

	record SearchCollectionResult(
			List<HaqqinAzSearchResultCard> cards,
			SearchCollectionStats stats,
			String failureMessage
	) {

		static SearchCollectionResult success(List<HaqqinAzSearchResultCard> cards, SearchCollectionStats stats) {
			return new SearchCollectionResult(cards, stats, null);
		}

		static SearchCollectionResult failed(String message) {
			return new SearchCollectionResult(List.of(), new SearchCollectionStats(), message);
		}
	}

	record SearchCardsResult(
			List<HaqqinAzSearchResultCard> cards,
			SearchCollectionStats stats
	) {
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

	private record ArticleCollectionResult(
			List<ScrapedPostDTO> posts,
			int articlePagesOpened,
			int dateParseFailures,
			int tooNewSkipped,
			int tooOldSkipped,
			int emptyTextSkipped,
			int articleErrors,
			int articleTimeouts,
			int duplicates,
			int mediaCollected
	) {

		private boolean hasExtractionFailures() {
			return dateParseFailures > 0 || emptyTextSkipped > 0 || articleErrors > 0 || articleTimeouts > 0;
		}

		private ScraperExecutionResult toFailedScraperResult(int candidatesFound) {
			String message = ("haqqin.az extraction failed: candidatesFound=%d, articlePagesOpened=%d, "
					+ "dateParseFailures=%d, tooNewSkipped=%d, tooOldSkipped=%d, "
					+ "emptyTextSkipped=%d, articleErrors=%d, articleTimeouts=%d, "
					+ "duplicateArticles=%d, postsCollected=0")
					.formatted(
							candidatesFound,
							articlePagesOpened,
							dateParseFailures,
							tooNewSkipped,
							tooOldSkipped,
							emptyTextSkipped,
							articleErrors,
							articleTimeouts,
							duplicates
					);
			return new ScraperExecutionResult(
					ScraperExecutionStatus.FAILED,
					List.of(),
					0,
					articlePagesOpened,
					message,
					null,
					null
			);
		}
	}

	static final class SearchCollectionStats {

		private int searchCardsFound;
		private int duplicatesSkipped;
		private int invalidUrlsSkipped;
		private int loadMoreClicks;
		private int batchesProcessed;
		private int dateParseFailures;
		private int tooNewSkipped;
		private int tooOldSkipped;

		int searchCardsFound() {
			return searchCardsFound;
		}

		int duplicatesSkipped() {
			return duplicatesSkipped;
		}

		int invalidUrlsSkipped() {
			return invalidUrlsSkipped;
		}

		int loadMoreClicks() {
			return loadMoreClicks;
		}

		int batchesProcessed() {
			return batchesProcessed;
		}

		int dateParseFailures() {
			return dateParseFailures;
		}

		int tooNewSkipped() {
			return tooNewSkipped;
		}

		int tooOldSkipped() {
			return tooOldSkipped;
		}

		void addSearchCardsFound(int value) {
			searchCardsFound += value;
		}

		void incrementDuplicatesSkipped() {
			duplicatesSkipped++;
		}

		void incrementInvalidUrlsSkipped() {
			invalidUrlsSkipped++;
		}

		void incrementLoadMoreClicks() {
			loadMoreClicks++;
		}

		void incrementBatchesProcessed() {
			batchesProcessed++;
		}

		void incrementDateParseFailures() {
			dateParseFailures++;
		}

		void incrementTooNewSkipped() {
			tooNewSkipped++;
		}

		void incrementTooOldSkipped() {
			tooOldSkipped++;
		}
	}

	private static final class BatchCollectionStats {

		private final int found;
		private int added;
		private int duplicates;
		private int invalidUrls;
		private int dateParseFailures;
		private int tooNew;
		private int tooOld;
		private int newDatedCards;

		private BatchCollectionStats(int found) {
			this.found = found;
		}

		int found() {
			return found;
		}

		int added() {
			return added;
		}

		int duplicates() {
			return duplicates;
		}

		int invalidUrls() {
			return invalidUrls;
		}

		int dateParseFailures() {
			return dateParseFailures;
		}

		int tooNew() {
			return tooNew;
		}

		int tooOld() {
			return tooOld;
		}

		void incrementAdded() {
			added++;
		}

		void incrementDuplicates() {
			duplicates++;
		}

		void incrementInvalidUrls() {
			invalidUrls++;
		}

		void incrementDateParseFailures() {
			dateParseFailures++;
		}

		void incrementTooNew() {
			tooNew++;
		}

		void incrementTooOld() {
			tooOld++;
		}

		void incrementNewDatedCards() {
			newDatedCards++;
		}

		boolean onlyNewDatedCardsAreOld() {
			return newDatedCards > 0 && tooOld == newDatedCards;
		}
	}
}
