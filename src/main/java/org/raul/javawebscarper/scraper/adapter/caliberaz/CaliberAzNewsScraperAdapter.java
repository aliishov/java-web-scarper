package org.raul.javawebscarper.scraper.adapter.caliberaz;

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
public class CaliberAzNewsScraperAdapter implements NewsScraperAdapter {

	private static final int MIN_KEYWORD_LENGTH = 3;
	private static final int MIN_ARTICLE_TEXT_LENGTH = 50;
	private static final int MIN_PARAGRAPH_TEXT_LENGTH = 20;
	private static final int MAX_CONSECUTIVE_NO_NEW_RESULTS = 2;
	private static final String ARTICLE_CLEANUP_SELECTOR = String.join(", ",
			"script",
			"style",
			"template",
			"iframe",
			"ins",
			".ad",
			".ads",
			".banner",
			"[class*=advert]",
			"[class*=reklam]",
			"[class*=banner]",
			".post_signature",
			".post_hits",
			".post_categoty"
	);
	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"caliber.az",
			"caliber.az",
			"Caliber.az",
			CaliberAzScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final CaliberAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return CaliberAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return CaliberAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("caliber.az search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		log.info(
				"Starting caliber.az scraping: source={}, keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
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
					"Finished caliber.az search collection: searchCardsFound={}, acceptedUrls={}, "
							+ "duplicateUrlsSkipped={}, invalidUrlsSkipped={}, scrollAttempts={}, "
							+ "scrollBatchesProcessed={}, dateParseFailures={}, tooNewSkipped={}, tooOldSkipped={}",
					searchResult.stats().searchCardsFound(),
					searchResult.cards().size(),
					searchResult.stats().duplicateUrlsSkipped(),
					searchResult.stats().invalidUrlsSkipped(),
					searchResult.stats().scrollAttempts(),
					searchResult.stats().scrollBatchesProcessed(),
					searchResult.stats().dateParseFailures(),
					searchResult.stats().tooNewSkipped(),
					searchResult.stats().tooOldSkipped()
			);

			ArticleCollectionResult articleResult = collectArticles(page, searchResult.cards(), context);
			log.info(
					"Finished caliber.az scraping: cardsFound={}, articlesOpened={}, postsCollected={}, "
							+ "scrollAttempts={}, articleTimeouts={}, dateParseFailures={}, tooNewSkipped={}, "
							+ "tooOldSkipped={}, emptyTextSkipped={}, coverImagesCollected={}, bodyImagesCollected={}",
					searchResult.stats().searchCardsFound(),
					articleResult.articlePagesOpened(),
					articleResult.posts().size(),
					searchResult.stats().scrollAttempts(),
					articleResult.articleTimeouts(),
					searchResult.stats().dateParseFailures() + articleResult.dateParseFailures(),
					searchResult.stats().tooNewSkipped() + articleResult.tooNewSkipped(),
					searchResult.stats().tooOldSkipped() + articleResult.tooOldSkipped(),
					articleResult.emptyTextSkipped(),
					articleResult.coverImagesCollected(),
					articleResult.bodyImagesCollected()
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
			log.warn("caliber.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("caliber.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected caliber.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected caliber.az scraping failure: " + exception.getMessage());
		}
	}

	private SearchCollectionResult openAndCollectSearchResults(
			BrowserPage page,
			String keyword,
			ScraperExecutionContext context
	) {
		if (!openSearchPage(page, keyword)) {
			String message = "caliber.az search page did not load";
			log.warn(message);
			return SearchCollectionResult.failed(message);
		}
		SearchCardsResult cardsResult = collectSearchResultCards(page, context);
		return SearchCollectionResult.success(cardsResult.cards(), cardsResult.stats());
	}

	private boolean openSearchPage(BrowserPage page, String keyword) {
		String fallbackUrl = searchUrl(keyword);
		try {
			log.info("Opening caliber.az search through UI flow: keyword={}", keyword);
			page.navigate(CaliberAzScraperSupport.BASE_URL);
			clickSearchButton(page);
			waitForSearchInput(page);
			fillSearchInput(page, keyword);
			page.waitForTimeout(1_000);
			if (!isSearchUrl(page.url())) {
				log.warn("caliber.az UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			}
		} catch (BrowserEngineException exception) {
			log.warn("caliber.az UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
		}
		boolean loaded = waitForSearchPage(page);
		log.info("caliber.az search page opened: url={}, loaded={}", page.url(), loaded);
		return loaded;
	}

	private void clickSearchButton(BrowserPage page) {
		try {
			page.waitForSelector(CaliberAzSelectors.SEARCH_OPEN_BUTTON, 5_000);
			page.click(CaliberAzSelectors.SEARCH_OPEN_BUTTON);
		} catch (BrowserEngineException exception) {
			page.waitForSelector(CaliberAzSelectors.SEARCH_OPEN_BUTTON_FALLBACK, 5_000);
			page.click(CaliberAzSelectors.SEARCH_OPEN_BUTTON_FALLBACK);
		}
	}

	private void waitForSearchInput(BrowserPage page) {
		try {
			page.waitForSelector(CaliberAzSelectors.SEARCH_INPUT, 5_000);
		} catch (BrowserEngineException exception) {
			page.waitForSelector(CaliberAzSelectors.SEARCH_INPUT_FALLBACK, 5_000);
		}
	}

	private void fillSearchInput(BrowserPage page, String keyword) {
		try {
			page.fill(CaliberAzSelectors.SEARCH_INPUT, "");
			page.fill(CaliberAzSelectors.SEARCH_INPUT, keyword);
			page.press(CaliberAzSelectors.SEARCH_INPUT, "Enter");
		} catch (BrowserEngineException exception) {
			page.fill(CaliberAzSelectors.SEARCH_INPUT_FALLBACK, "");
			page.fill(CaliberAzSelectors.SEARCH_INPUT_FALLBACK, keyword);
			page.press(CaliberAzSelectors.SEARCH_INPUT_FALLBACK, "Enter");
		}
	}

	private boolean waitForSearchPage(BrowserPage page) {
		try {
			page.waitForSelector("body", 10_000);
			try {
				page.waitForSelector(CaliberAzSelectors.SEARCH_RESULT_CARD, 5_000);
			} catch (BrowserTimeoutException exception) {
				log.debug("caliber.az search result cards did not appear before timeout: url={}", page.url());
			}
			return isSearchUrl(page.url());
		} catch (BrowserTimeoutException exception) {
			return false;
		}
	}

	String searchUrl(String keyword) {
		return CaliberAzScraperSupport.BASE_URL + "search/" + encodePathSegment(keyword);
	}

	private String encodePathSegment(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	SearchCardsResult collectSearchResultCards(BrowserPage page, ScraperExecutionContext context) {
		Map<String, CaliberAzSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();
		SearchCollectionStats totalStats = new SearchCollectionStats();
		int consecutiveNoNewResults = 0;

		for (int batch = 1; batch <= context.maxPages() && cardsByUrl.size() < context.maxPosts(); batch++) {
			Document document = Jsoup.parse(page.content(), CaliberAzScraperSupport.BASE_URL);
			BatchCollectionStats batchStats = collectCurrentCards(document, batch, context, cardsByUrl, seenUrls, totalStats);
			totalStats.incrementScrollBatchesProcessed();
			log.info(
					"caliber.az search batch={} stats: found={}, added={}, duplicates={}, invalidUrls={}, "
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
				log.info("Stopping caliber.az scroll because batch {} is older than dateFrom", batch);
				break;
			}
			if (batchStats.added() == 0) {
				consecutiveNoNewResults++;
			} else {
				consecutiveNoNewResults = 0;
			}
			if (consecutiveNoNewResults >= MAX_CONSECUTIVE_NO_NEW_RESULTS) {
				log.info("Stopping caliber.az scroll after {} batches without new URLs", consecutiveNoNewResults);
				break;
			}
			if (!scrollAndWaitForNewCards(page, seenUrls)) {
				consecutiveNoNewResults++;
				if (consecutiveNoNewResults >= MAX_CONSECUTIVE_NO_NEW_RESULTS) {
					break;
				}
			}
			totalStats.incrementScrollAttempts();
		}
		if (!cardsByUrl.isEmpty()) {
			log.info("caliber.az candidate URLs: {}", cardsByUrl.keySet().stream().limit(10).toList());
		}
		return new SearchCardsResult(cardsByUrl.values().stream().limit(context.maxPosts()).toList(), totalStats);
	}

	private BatchCollectionStats collectCurrentCards(
			Document document,
			int batch,
			ScraperExecutionContext context,
			Map<String, CaliberAzSearchResultCard> cardsByUrl,
			Set<String> seenUrls,
			SearchCollectionStats totalStats
	) {
		Elements elements = document.select(CaliberAzSelectors.SEARCH_RESULT_CARD);
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
			CaliberAzSearchResultCard card = parsedResult.card();
			if (!seenUrls.add(card.postUrl())) {
				stats.incrementDuplicates();
				totalStats.incrementDuplicateUrlsSkipped();
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

	SearchCardParseResult parseResultCard(Element element, int scrollBatch) {
		Element link = element.select(CaliberAzSelectors.SEARCH_RESULT_LINK)
				.stream()
				.filter(candidate -> CaliberAzScraperSupport.isPostUrl(candidate.attr("href")))
				.findFirst()
				.orElse(null);
		if (link == null) {
			return SearchCardParseResult.skipped(SearchCardSkipReason.EXTERNAL_OR_INVALID_URL);
		}
		String normalizedUrl = CaliberAzScraperSupport.normalizePostUrl(link.attr("href")).orElse(null);
		if (normalizedUrl == null) {
			return SearchCardParseResult.skipped(SearchCardSkipReason.EXTERNAL_OR_INVALID_URL);
		}
		String dateText = text(element, CaliberAzSelectors.SEARCH_RESULT_DATE);
		OffsetDateTime searchDate = null;
		if (dateText != null && !dateText.isBlank()) {
			searchDate = dateParser.parseArticleDate(dateText, normalizedUrl).orElse(null);
		}
		String thumbnailUrl = extractSearchThumbnailUrl(element, normalizedUrl).orElse(null);
		return SearchCardParseResult.card(new CaliberAzSearchResultCard(
				normalizedUrl,
				text(element, CaliberAzSelectors.SEARCH_RESULT_TITLE),
				searchDate,
				thumbnailUrl,
				scrollBatch
		));
	}

	private boolean scrollAndWaitForNewCards(BrowserPage page, Set<String> alreadySeenUrls) {
		Document before = Jsoup.parse(page.content(), CaliberAzScraperSupport.BASE_URL);
		int previousCount = before.select(CaliberAzSelectors.SEARCH_RESULT_CARD).size();
		Set<String> previousUrls = collectPostUrls(before);
		page.scrollToBottom(1, 1_000);
		for (int attempt = 0; attempt < 5; attempt++) {
			page.waitForTimeout(700);
			Document after = Jsoup.parse(page.content(), CaliberAzScraperSupport.BASE_URL);
			int currentCount = after.select(CaliberAzSelectors.SEARCH_RESULT_CARD).size();
			Set<String> currentUrls = collectPostUrls(after);
			boolean hasNewUrl = currentUrls.stream()
					.anyMatch(url -> !previousUrls.contains(url) && !alreadySeenUrls.contains(url));
			if (currentCount > previousCount || hasNewUrl) {
				return true;
			}
		}
		log.warn("Stopping caliber.az scroll because no new cards appeared after scroll");
		return false;
	}

	private Set<String> collectPostUrls(Document document) {
		Set<String> urls = new LinkedHashSet<>();
		for (Element element : document.select(CaliberAzSelectors.SEARCH_RESULT_CARD)) {
			element.select(CaliberAzSelectors.SEARCH_RESULT_LINK)
					.stream()
					.map(link -> CaliberAzScraperSupport.normalizePostUrl(link.attr("href")))
					.flatMap(Optional::stream)
					.forEach(urls::add);
		}
		return urls;
	}

	private ArticleCollectionResult collectArticles(
			BrowserPage page,
			List<CaliberAzSearchResultCard> cards,
			ScraperExecutionContext context
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenExternalIds = new LinkedHashSet<>();
		Set<String> seenPostUrls = new LinkedHashSet<>();
		ArticleCounters counters = new ArticleCounters();

		for (CaliberAzSearchResultCard card : cards) {
			if (posts.size() >= context.maxPosts()) {
				break;
			}
			counters.articlePagesOpened++;
			try {
				ArticleCollectionAttempt attempt = collectArticleWithRetry(page, card, context);
				if (attempt.post() == null) {
					counters.incrementSkip(attempt.skipReason());
					continue;
				}
				ScrapedPostDTO post = attempt.post();
				if (seenExternalIds.add(post.externalPostId()) && seenPostUrls.add(post.postUrl())) {
					counters.coverImagesCollected += attempt.coverImagesCollected();
					counters.bodyImagesCollected += attempt.bodyImagesCollected();
					posts.add(post);
				} else {
					counters.duplicates++;
					log.debug("Skipping duplicate caliber.az article: url={}, externalPostId={}", post.postUrl(), post.externalPostId());
				}
			} catch (BrowserTimeoutException exception) {
				counters.articleTimeouts++;
				log.warn("Skipping caliber.az article after timeout: url={}", card.postUrl());
			} catch (RuntimeException exception) {
				counters.articleErrors++;
				log.warn("Skipping caliber.az article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return counters.toResult(posts);
	}

	private ArticleCollectionAttempt collectArticleWithRetry(
			BrowserPage page,
			CaliberAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		try {
			return collectArticle(page, card, context);
		} catch (BrowserTimeoutException exception) {
			log.warn("Timeout opening caliber.az article, retrying once: url={}", card.postUrl());
			return collectArticle(page, card, context);
		}
	}

	private ArticleCollectionAttempt collectArticle(
			BrowserPage page,
			CaliberAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		page.navigate(card.postUrl());
		page.waitForTimeout(500);
		Document document = Jsoup.parse(page.content(), card.postUrl());
		Optional<OffsetDateTime> parsedPostDate = parseArticleDate(document, card);
		if (parsedPostDate.isEmpty()) {
			log.warn("Skipping caliber.az article with missing or invalid date: url={}", card.postUrl());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.INVALID_DATE);
		}
		OffsetDateTime postDate = parsedPostDate.get();
		if (DateRangeValidator.isAfterRange(postDate, context.dateTo())) {
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.TOO_NEW);
		}
		if (DateRangeValidator.isBeforeRange(postDate, context.dateFrom())) {
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.TOO_OLD);
		}

		String text = extractArticleText(document);
		if (text == null || text.isBlank()) {
			log.warn("Skipping caliber.az article with empty text: url={}, title={}", card.postUrl(), card.title());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.EMPTY_TEXT);
		}

		String externalPostId = CaliberAzScraperSupport.extractExternalPostId(card.postUrl())
				.orElseGet(() -> Integer.toHexString(card.postUrl().hashCode()));
		String title = firstNonBlank(
				text(document, CaliberAzSelectors.ARTICLE_ROOT + " " + CaliberAzSelectors.ARTICLE_TITLE),
				text(document, CaliberAzSelectors.ARTICLE_ROOT + " " + CaliberAzSelectors.ARTICLE_TITLE_FALLBACK),
				card.title()
		);
		MediaExtractionResult mediaResult = extractMedia(document, card);
		Map<String, Object> metadata = metadata(context, card, title);

		log.info("Collected caliber.az article: url={}, mediaCount={}", card.postUrl(), mediaResult.media().size());
		return ArticleCollectionAttempt.collected(
				new ScrapedPostDTO(
						externalPostId,
						card.postUrl(),
						postDate,
						AUTHOR,
						text,
						"ru",
						mediaResult.media(),
						metadata
				),
				mediaResult.coverImagesCollected(),
				mediaResult.bodyImagesCollected()
		);
	}

	Optional<OffsetDateTime> parseArticleDate(Document document, CaliberAzSearchResultCard card) {
		Element root = document.selectFirst(CaliberAzSelectors.ARTICLE_ROOT);
		if (root == null) {
			return Optional.ofNullable(card.searchDate());
		}
		for (Element element : root.select(CaliberAzSelectors.ARTICLE_DATE)) {
			Optional<OffsetDateTime> parsedDate = dateParser.parseArticleDate(element.text(), card.postUrl());
			if (parsedDate.isPresent()) {
				return parsedDate;
			}
		}
		return Optional.ofNullable(card.searchDate());
	}

	String extractArticleText(Document document) {
		Element root = document.selectFirst(CaliberAzSelectors.ARTICLE_ROOT);
		if (root == null) {
			return null;
		}
		Element body = root.selectFirst(CaliberAzSelectors.ARTICLE_BODY);
		if (body == null) {
			return firstMetaContent(document, "meta[name=description]", "meta[property=og:description]")
					.map(this::normalizeArticleText)
					.filter(this::isUsableArticleText)
					.orElse(null);
		}
		Element cleanBody = cleanedArticleBody(body);
		String text = cleanBody.select("p")
				.stream()
				.map(Element::text)
				.map(this::normalizeArticleText)
				.filter(this::isArticleParagraph)
				.reduce((left, right) -> left + "\n" + right)
				.orElse(null);
		if (isUsableArticleText(text)) {
			return text;
		}
		return firstMetaContent(document, "meta[name=description]", "meta[property=og:description]")
				.map(this::normalizeArticleText)
				.filter(this::isUsableArticleText)
				.orElse(null);
	}

	private Element cleanedArticleBody(Element body) {
		Element cleanBody = body.clone();
		cleanBody.select(ARTICLE_CLEANUP_SELECTOR).remove();
		return cleanBody;
	}

	private boolean isArticleParagraph(String text) {
		if (text == null || text.length() < MIN_PARAGRAPH_TEXT_LENGTH) {
			return false;
		}
		String normalized = text.toLowerCase(Locale.ROOT);
		return !normalized.contains("просмотров:")
				&& !normalized.contains("post_signature")
				&& !normalized.contains("conversation-turn")
				&& !normalized.contains("text-token-text-primary")
				&& !normalized.contains("data-testid")
				&& !normalized.contains("реклама");
	}

	private boolean isUsableArticleText(String text) {
		return text != null && text.length() >= MIN_ARTICLE_TEXT_LENGTH;
	}

	MediaExtractionResult extractMedia(Document document, CaliberAzSearchResultCard card) {
		Element root = document.selectFirst(CaliberAzSelectors.ARTICLE_ROOT);
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		int coverImagesCollected = 0;
		int bodyImagesCollected = 0;
		if (root != null) {
			Optional<String> coverUrl = extractBackgroundImageUrl(root, CaliberAzSelectors.ARTICLE_COVER, card.postUrl());
			if (coverUrl.isPresent()) {
				mediaByUrl.put(coverUrl.get(), MediaType.IMAGE);
				coverImagesCollected = 1;
			}
			for (Element image : root.select(CaliberAzSelectors.ARTICLE_BODY_IMAGE)) {
				String rawUrl = firstNonBlank(image.attr("src"), image.attr("data-src"));
				if (rawUrl == null || !CaliberAzScraperSupport.isAllowedMediaUrl(rawUrl)) {
					continue;
				}
				String normalizedUrl = UrlNormalizer.resolve(card.postUrl(), rawUrl);
				if (CaliberAzScraperSupport.isAllowedMediaUrl(normalizedUrl)
						&& !mediaByUrl.containsKey(normalizedUrl)) {
					mediaByUrl.put(normalizedUrl, MediaType.IMAGE);
					bodyImagesCollected++;
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
		return new MediaExtractionResult(media, coverImagesCollected, bodyImagesCollected);
	}

	private Optional<String> extractSearchThumbnailUrl(Element element, String baseUrl) {
		return extractBackgroundImageUrl(element, CaliberAzSelectors.SEARCH_RESULT_THUMBNAIL, baseUrl);
	}

	private Optional<String> extractBackgroundImageUrl(Element root, String selector, String baseUrl) {
		Element image = root.selectFirst(selector);
		if (image == null) {
			return Optional.empty();
		}
		return CaliberAzScraperSupport.extractBackgroundImageUrl(image.attr("style"))
				.map(rawUrl -> UrlNormalizer.resolve(baseUrl, rawUrl))
				.filter(CaliberAzScraperSupport::isAllowedMediaUrl);
	}

	private Map<String, Object> metadata(ScraperExecutionContext context, CaliberAzSearchResultCard card, String title) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", CaliberAzScraperSupport.SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		metadata.put("scrollBatch", card.scrollBatch());
		if (title != null && !title.isBlank()) {
			metadata.put("title", title);
		}
		return metadata;
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
			CaliberAzSearchResultCard card,
			SearchCardSkipReason skipReason
	) {

		static SearchCardParseResult card(CaliberAzSearchResultCard card) {
			return new SearchCardParseResult(card, null);
		}

		static SearchCardParseResult skipped(SearchCardSkipReason reason) {
			return new SearchCardParseResult(null, reason);
		}
	}

	record SearchCollectionResult(
			List<CaliberAzSearchResultCard> cards,
			SearchCollectionStats stats,
			String failureMessage
	) {

		static SearchCollectionResult success(List<CaliberAzSearchResultCard> cards, SearchCollectionStats stats) {
			return new SearchCollectionResult(cards, stats, null);
		}

		static SearchCollectionResult failed(String message) {
			return new SearchCollectionResult(List.of(), new SearchCollectionStats(), message);
		}
	}

	record SearchCardsResult(
			List<CaliberAzSearchResultCard> cards,
			SearchCollectionStats stats
	) {
	}

	record MediaExtractionResult(
			List<ScrapedMediaDTO> media,
			int coverImagesCollected,
			int bodyImagesCollected
	) {
	}

	private record ArticleCollectionAttempt(
			ScrapedPostDTO post,
			ArticleSkipReason skipReason,
			int coverImagesCollected,
			int bodyImagesCollected
	) {

		private static ArticleCollectionAttempt collected(
				ScrapedPostDTO post,
				int coverImagesCollected,
				int bodyImagesCollected
		) {
			return new ArticleCollectionAttempt(post, null, coverImagesCollected, bodyImagesCollected);
		}

		private static ArticleCollectionAttempt skipped(ArticleSkipReason reason) {
			return new ArticleCollectionAttempt(null, reason, 0, 0);
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
			int coverImagesCollected,
			int bodyImagesCollected
	) {

		private boolean hasExtractionFailures() {
			return dateParseFailures > 0 || emptyTextSkipped > 0 || articleErrors > 0 || articleTimeouts > 0;
		}

		private ScraperExecutionResult toFailedScraperResult(int candidatesFound) {
			String message = ("caliber.az extraction failed: candidatesFound=%d, articlePagesOpened=%d, "
					+ "dateParseFailures=%d, tooNewSkipped=%d, tooOldSkipped=%d, emptyTextSkipped=%d, "
					+ "articleErrors=%d, articleTimeouts=%d, duplicateArticles=%d, postsCollected=0")
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
		private int duplicateUrlsSkipped;
		private int invalidUrlsSkipped;
		private int scrollAttempts;
		private int scrollBatchesProcessed;
		private int dateParseFailures;
		private int tooNewSkipped;
		private int tooOldSkipped;

		int searchCardsFound() {
			return searchCardsFound;
		}

		int duplicateUrlsSkipped() {
			return duplicateUrlsSkipped;
		}

		int invalidUrlsSkipped() {
			return invalidUrlsSkipped;
		}

		int scrollAttempts() {
			return scrollAttempts;
		}

		int scrollBatchesProcessed() {
			return scrollBatchesProcessed;
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

		void incrementDuplicateUrlsSkipped() {
			duplicateUrlsSkipped++;
		}

		void incrementInvalidUrlsSkipped() {
			invalidUrlsSkipped++;
		}

		void incrementScrollAttempts() {
			scrollAttempts++;
		}

		void incrementScrollBatchesProcessed() {
			scrollBatchesProcessed++;
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

	private static final class ArticleCounters {

		private int articlePagesOpened;
		private int dateParseFailures;
		private int tooNewSkipped;
		private int tooOldSkipped;
		private int emptyTextSkipped;
		private int articleErrors;
		private int articleTimeouts;
		private int duplicates;
		private int coverImagesCollected;
		private int bodyImagesCollected;

		private void incrementSkip(ArticleSkipReason reason) {
			if (reason == ArticleSkipReason.INVALID_DATE) {
				dateParseFailures++;
			} else if (reason == ArticleSkipReason.TOO_NEW) {
				tooNewSkipped++;
			} else if (reason == ArticleSkipReason.TOO_OLD) {
				tooOldSkipped++;
			} else if (reason == ArticleSkipReason.EMPTY_TEXT) {
				emptyTextSkipped++;
			}
		}

		private ArticleCollectionResult toResult(List<ScrapedPostDTO> posts) {
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
					coverImagesCollected,
					bodyImagesCollected
			);
		}
	}
}
