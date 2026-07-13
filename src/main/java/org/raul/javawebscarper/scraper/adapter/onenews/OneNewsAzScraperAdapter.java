package org.raul.javawebscarper.scraper.adapter.onenews;

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
import java.util.Comparator;
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
public class OneNewsAzScraperAdapter implements NewsScraperAdapter {

	private static final int MIN_KEYWORD_LENGTH = 3;
	private static final int MIN_ARTICLE_TEXT_LENGTH = 50;
	private static final int MIN_PARAGRAPH_TEXT_LENGTH = 20;
	private static final String ARTICLE_CLEANUP_SELECTOR = String.join(", ",
			"script",
			"style",
			"template",
			"iframe",
			"ins",
			".AdviadNativeVideo",
			".leftColumnBanner",
			".leftColumnMobileBanner",
			".thumb",
			"[id*=ad]",
			"[class*=banner]",
			"[class*=advert]",
			"[class*=reklam]",
			"[class*=related]",
			"[class*=share]",
			"[class*=comment]",
			"[class*=social]",
			"[class*=footer]",
			"[class*=sidebar]"
	);

	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"1news.az",
			"1news.az",
			"1news.az",
			OneNewsAzScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final OneNewsAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return OneNewsAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return OneNewsAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("1news.az search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		log.info(
				"Starting 1news.az scraping: source={}, keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
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
					"Finished 1news.az search collection: searchResultsFound={}, accepted1NewsUrls={}, "
							+ "externalUrlsSkipped={}, duplicatesSkipped={}, pagesProcessed={}, "
							+ "dateParseFailures={}, outOfRangeSkipped={}, sortingConfirmed={}",
					searchResult.stats().searchResultsFound(),
					searchResult.cards().size(),
					searchResult.stats().externalUrlsSkipped(),
					searchResult.stats().duplicatesSkipped(),
					searchResult.stats().pagesProcessed(),
					searchResult.stats().dateParseFailures(),
					searchResult.stats().outOfRangeSkipped(),
					searchResult.sortingConfirmed()
			);
			ArticleCollectionResult articleResult = collectArticles(page, searchResult.cards(), context);
			log.info(
					"Finished 1news.az scraping: searchResultsFound={}, accepted1NewsUrls={}, "
							+ "externalUrlsSkipped={}, duplicatesSkipped={}, pagesProcessed={}, "
							+ "dateParseFailures={}, outOfRangeSkipped={}, articlesOpened={}, articlesSaved={}, "
							+ "articlesSkippedEmptyText={}, articleTimeouts={}, sortingConfirmed={}",
					searchResult.stats().searchResultsFound(),
					searchResult.cards().size(),
					searchResult.stats().externalUrlsSkipped(),
					searchResult.stats().duplicatesSkipped() + articleResult.duplicates(),
					searchResult.stats().pagesProcessed(),
					searchResult.stats().dateParseFailures() + articleResult.invalidDates(),
					searchResult.stats().outOfRangeSkipped() + articleResult.skippedOutOfRange(),
					articleResult.articlesOpened(),
					articleResult.posts().size(),
					articleResult.skippedEmptyText(),
					articleResult.articleTimeouts(),
					searchResult.sortingConfirmed()
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
			log.warn("1news.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("1news.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected 1news.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected 1news.az scraping failure: " + exception.getMessage());
		}
	}

	private ArticleCollectionResult collectArticles(
			BrowserPage page,
			List<OneNewsAzSearchResultCard> cards,
			ScraperExecutionContext context
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenExternalIds = new LinkedHashSet<>();
		Set<String> seenPostUrls = new LinkedHashSet<>();
		int articlesOpened = 0;
		int skippedEmptyText = 0;
		int skippedOutOfRange = 0;
		int invalidDates = 0;
		int skippedErrors = 0;
		int articleTimeouts = 0;
		int duplicates = 0;

		for (OneNewsAzSearchResultCard card : cards) {
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
					} else if (attempt.skipReason() == ArticleSkipReason.INVALID_DATE) {
						invalidDates++;
					}
					continue;
				}
				ScrapedPostDTO post = attempt.post();
				if (seenExternalIds.add(post.externalPostId()) && seenPostUrls.add(post.postUrl())) {
					posts.add(post);
				} else {
					duplicates++;
					log.debug("Skipping duplicate 1news.az article: url={}, externalPostId={}", post.postUrl(), post.externalPostId());
				}
			} catch (BrowserTimeoutException exception) {
				articleTimeouts++;
				log.warn("Skipping 1news.az article after timeout: url={}", card.postUrl());
			} catch (RuntimeException exception) {
				skippedErrors++;
				log.warn("Skipping 1news.az article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return new ArticleCollectionResult(
				posts,
				articlesOpened,
				skippedEmptyText,
				skippedOutOfRange,
				invalidDates,
				skippedErrors,
				articleTimeouts,
				duplicates
		);
	}

	private ArticleCollectionAttempt collectArticleWithRetry(
			BrowserPage page,
			OneNewsAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		try {
			return collectArticle(page, card, context);
		} catch (BrowserTimeoutException exception) {
			log.warn("Timeout opening 1news.az article, retrying once: url={}", card.postUrl());
			return collectArticle(page, card, context);
		}
	}

	private ArticleCollectionAttempt collectArticle(
			BrowserPage page,
			OneNewsAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		page.navigate(card.postUrl());
		page.waitForTimeout(500);
		Document document = Jsoup.parse(page.content(), card.postUrl());
		Optional<OffsetDateTime> parsedPostDate = parseArticleDate(document, card);
		if (parsedPostDate.isEmpty()) {
			log.warn("Skipping 1news.az article with missing or invalid date: url={}", card.postUrl());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.INVALID_DATE);
		}
		OffsetDateTime postDate = parsedPostDate.get();
		if (!DateRangeValidator.isInsideRange(postDate, context.dateFrom(), context.dateTo())) {
			log.debug("Skipping 1news.az article outside date range: url={}, postDate={}", card.postUrl(), postDate);
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.OUT_OF_RANGE);
		}

		String text = extractArticleText(document);
		if (text == null || text.isBlank()) {
			log.warn("Skipping 1news.az article with empty text: url={}, title={}", card.postUrl(), card.title());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.EMPTY_TEXT);
		}

		String externalPostId = OneNewsAzScraperSupport.extractExternalPostId(card.postUrl())
				.orElseGet(() -> Integer.toHexString(card.postUrl().hashCode()));
		String title = firstNonBlank(text(document, OneNewsAzSelectors.ARTICLE_ROOT + " " + OneNewsAzSelectors.ARTICLE_TITLE), card.title());
		List<ScrapedMediaDTO> media = extractMedia(document, card);
		Map<String, Object> metadata = metadata(context, card, title, originalArticleAuthor(document));

		log.info("Collected 1news.az article: url={}, mediaCount={}", card.postUrl(), media.size());
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

	Optional<OffsetDateTime> parseArticleDate(Document document, OneNewsAzSearchResultCard card) {
		Element root = document.selectFirst(OneNewsAzSelectors.ARTICLE_ROOT);
		if (root == null) {
			return Optional.ofNullable(card.searchDate());
		}
		for (Element element : root.select(OneNewsAzSelectors.ARTICLE_DATE)) {
			Optional<OffsetDateTime> parsedDate = dateParser.parseArticleDate(element.text(), card.postUrl());
			if (parsedDate.isPresent()) {
				return parsedDate;
			}
		}
		return Optional.ofNullable(card.searchDate());
	}

	String extractArticleText(Document document) {
		Element root = document.selectFirst(OneNewsAzSelectors.ARTICLE_ROOT);
		if (root == null) {
			return null;
		}
		Element content = root.selectFirst(OneNewsAzSelectors.ARTICLE_CONTENT);
		if (content == null) {
			return null;
		}
		Element cleanContent = cleanedArticleContent(content);
		List<Element> paragraphs = cleanContent.children()
				.stream()
				.filter(element -> "p".equals(element.normalName()))
				.toList();
		if (paragraphs.isEmpty()) {
			paragraphs = cleanContent.select("p");
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
		String normalized = normalizeText(text);
		return !normalized.equals("reklam")
				&& !normalized.contains("adviadnativevideo")
				&& !normalized.contains("get_ads.js")
				&& !normalized.contains("window._ttzi")
				&& !normalized.contains("yandex")
				&& !normalized.contains("googleads");
	}

	private boolean isUsableArticleText(String text) {
		return text != null && text.length() >= MIN_ARTICLE_TEXT_LENGTH;
	}

	List<ScrapedMediaDTO> extractMedia(Document document, OneNewsAzSearchResultCard card) {
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
		Element root = document.selectFirst(OneNewsAzSelectors.ARTICLE_ROOT);
		if (root == null) {
			return Optional.empty();
		}
		Element image = root.selectFirst(OneNewsAzSelectors.ARTICLE_MAIN_IMAGE);
		if (image == null) {
			return Optional.empty();
		}
		String rawUrl = firstNonBlank(
				image.attr("src"),
				image.attr("data-src"),
				firstSrcsetUrl(image.attr("srcset")),
				firstSrcsetUrl(image.attr("data-srcset"))
		);
		if (rawUrl == null || !OneNewsAzScraperSupport.isAllowedMediaUrl(rawUrl)) {
			return Optional.empty();
		}
		String normalizedUrl = UrlNormalizer.resolve(baseUrl, rawUrl);
		return OneNewsAzScraperSupport.isAllowedMediaUrl(normalizedUrl)
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

	private String originalArticleAuthor(Document document) {
		return text(document, OneNewsAzSelectors.ARTICLE_ROOT + " " + OneNewsAzSelectors.ARTICLE_AUTHOR);
	}

	private Map<String, Object> metadata(
			ScraperExecutionContext context,
			OneNewsAzSearchResultCard card,
			String title,
			String originalArticleAuthor
	) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", OneNewsAzScraperSupport.SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		metadata.put("searchPageNumber", card.searchPageNumber());
		if (title != null && !title.isBlank()) {
			metadata.put("title", title);
		}
		if (originalArticleAuthor != null && !originalArticleAuthor.isBlank()) {
			metadata.put("originalArticleAuthor", originalArticleAuthor);
		}
		return metadata;
	}

	private SearchCollectionResult openAndCollectSearchResults(
			BrowserPage page,
			String keyword,
			ScraperExecutionContext context
	) {
		if (!openSearchPage(page, keyword)) {
			String message = "1news.az search page did not load results container";
			log.warn(message);
			return SearchCollectionResult.failed(message);
		}
		boolean sortingConfirmed = selectDateSorting(page);
		SearchCardsResult cardsResult = collectSearchResultCards(page, context, sortingConfirmed);
		return SearchCollectionResult.success(cardsResult.cards(), sortingConfirmed, cardsResult.stats());
	}

	private boolean openSearchPage(BrowserPage page, String keyword) {
		String fallbackUrl = searchUrl(keyword);
		try {
			log.info("Opening 1news.az search through UI flow: keyword={}", keyword);
			page.navigate(OneNewsAzScraperSupport.BASE_URL);
			waitForSearchInput(page);
			fillSearchInput(page, keyword);
			page.waitForTimeout(1_000);
			if (!isSearchUrl(page.url())) {
				log.warn("1news.az UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			}
		} catch (BrowserEngineException exception) {
			log.warn("1news.az UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
		}
		boolean loaded = waitForSearchResults(page);
		log.info("1news.az search page opened: url={}, resultsContainerLoaded={}", page.url(), loaded);
		return loaded;
	}

	private void waitForSearchInput(BrowserPage page) {
		try {
			page.waitForSelector(OneNewsAzSelectors.SEARCH_INPUT, 5_000);
		} catch (BrowserTimeoutException exception) {
			page.waitForSelector(OneNewsAzSelectors.SEARCH_INPUT_FALLBACK, 5_000);
		}
	}

	private void fillSearchInput(BrowserPage page, String keyword) {
		try {
			page.fill(OneNewsAzSelectors.SEARCH_INPUT, keyword);
			page.press(OneNewsAzSelectors.SEARCH_INPUT, "Enter");
		} catch (BrowserEngineException exception) {
			page.fill(OneNewsAzSelectors.SEARCH_INPUT_FALLBACK, keyword);
			page.press(OneNewsAzSelectors.SEARCH_INPUT_FALLBACK, "Enter");
		}
	}

	private boolean waitForSearchResults(BrowserPage page) {
		try {
			page.waitForSelector(OneNewsAzSelectors.SEARCH_RESULTS_CONTAINER, 10_000);
			page.waitForTimeout(1_000);
			return true;
		} catch (BrowserTimeoutException exception) {
			log.warn("1news.az Google CSE results container did not appear: url={}", page.url());
			return false;
		}
	}

	private boolean selectDateSorting(BrowserPage page) {
		try {
			String firstUrlBefore = firstResultUrl(page.content()).orElse(null);
			page.click(OneNewsAzSelectors.SORT_SELECTED);
			page.waitForTimeout(300);
			page.click(".gsc-option-menu-item:has-text(\"Date\")");
			page.waitForTimeout(1_500);
			waitForSearchResults(page);
			Document document = Jsoup.parse(page.content(), OneNewsAzScraperSupport.ROOT_URL);
			String selectedText = text(document, OneNewsAzSelectors.SORT_SELECTED_TEXT);
			boolean selectedDate = selectedText != null && normalizeText(selectedText).contains("date");
			boolean firstUrlChanged = firstUrlBefore != null
					&& firstResultUrl(document).filter(firstUrl -> !firstUrl.equals(firstUrlBefore)).isPresent();
			if (selectedDate || firstUrlChanged) {
				log.info("1news.az Google CSE Date sorting confirmed: selectedText={}", selectedText);
				return true;
			}
			log.warn("1news.az Date sorting was clicked but not confirmed: selectedText={}", selectedText);
			return false;
		} catch (BrowserEngineException exception) {
			log.warn("1news.az Date sorting failed, continuing cautiously: {}", exception.getMessage());
			return false;
		}
	}

	String searchUrl(String keyword) {
		return OneNewsAzScraperSupport.BASE_URL + "/axtarish/?q="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8);
	}

	SearchCardsResult collectSearchResultCards(
			BrowserPage page,
			ScraperExecutionContext context,
			boolean sortingConfirmed
	) {
		Map<String, OneNewsAzSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();
		Set<Integer> visitedPages = new LinkedHashSet<>();
		SearchCollectionStats stats = new SearchCollectionStats();

		for (int pageIndex = 0; pageIndex < context.maxPages() && cardsByUrl.size() < context.maxPosts(); pageIndex++) {
			Document document = Jsoup.parse(page.content(), OneNewsAzScraperSupport.ROOT_URL);
			int currentPage = currentPageNumber(document);
			if (!visitedPages.add(currentPage)) {
				log.warn("Stopping 1news.az pagination because page {} was already visited", currentPage);
				break;
			}
			stats.incrementPagesProcessed();
			PageCollectionStats pageStats = collectCurrentPageCards(
					document,
					currentPage,
					context,
					cardsByUrl,
					seenUrls,
					stats
			);
			log.info(
					"1news.az search page={} stats: found={}, added={}, externalSkipped={}, "
							+ "duplicatesSkipped={}, dateParseFailures={}, outOfRangeSkipped={}",
					currentPage,
					pageStats.found(),
					pageStats.added(),
					pageStats.externalUrlsSkipped(),
					pageStats.duplicatesSkipped(),
					pageStats.dateParseFailures(),
					pageStats.outOfRangeSkipped()
			);

			if (cardsByUrl.size() >= context.maxPosts()) {
				break;
			}
			if (sortingConfirmed && pageStats.onlyAcceptedDatedCardsAreOld()) {
				log.info("Stopping 1news.az pagination because Date-sorted page {} is older than dateFrom", currentPage);
				break;
			}
			Optional<Integer> nextPage = nextPageNumber(document, visitedPages);
			if (nextPage.isEmpty()) {
				break;
			}
			if (!clickNextPage(page, nextPage.get())) {
				break;
			}
		}
		if (!cardsByUrl.isEmpty()) {
			log.info("1news.az candidate URLs: {}", cardsByUrl.keySet().stream().limit(10).toList());
		}
		return new SearchCardsResult(cardsByUrl.values().stream().limit(context.maxPosts()).toList(), stats);
	}

	private PageCollectionStats collectCurrentPageCards(
			Document document,
			int currentPage,
			ScraperExecutionContext context,
			Map<String, OneNewsAzSearchResultCard> cardsByUrl,
			Set<String> seenUrls,
			SearchCollectionStats totalStats
	) {
		Elements elements = document.select(OneNewsAzSelectors.SEARCH_RESULT);
		PageCollectionStats pageStats = new PageCollectionStats(elements.size());
		totalStats.addSearchResultsFound(elements.size());
		for (Element element : elements) {
			SearchCardParseResult parsedResult = parseResultCard(element, currentPage);
			if (parsedResult.card() == null) {
				if (parsedResult.skipReason() == SearchCardSkipReason.EXTERNAL_OR_INVALID_URL) {
					pageStats.incrementExternalUrlsSkipped();
					totalStats.incrementExternalUrlsSkipped();
				} else if (parsedResult.skipReason() == SearchCardSkipReason.DATE_PARSE) {
					pageStats.incrementDateParseFailures();
					totalStats.incrementDateParseFailures();
				}
				continue;
			}
			OneNewsAzSearchResultCard card = parsedResult.card();
			if (!seenUrls.add(card.postUrl())) {
				pageStats.incrementDuplicatesSkipped();
				totalStats.incrementDuplicatesSkipped();
				continue;
			}
			if (card.searchDate() != null && DateRangeValidator.isAfterRange(card.searchDate(), context.dateTo())) {
				pageStats.incrementOutOfRangeSkipped();
				totalStats.incrementOutOfRangeSkipped();
				continue;
			}
			if (card.searchDate() != null && DateRangeValidator.isBeforeRange(card.searchDate(), context.dateFrom())) {
				pageStats.incrementOutOfRangeSkipped();
				totalStats.incrementOutOfRangeSkipped();
				pageStats.incrementBeforeRangeDatedCards();
				continue;
			}
			cardsByUrl.put(card.postUrl(), card);
			pageStats.incrementAdded();
			if (cardsByUrl.size() >= context.maxPosts()) {
				break;
			}
		}
		return pageStats;
	}

	SearchCardParseResult parseResultCard(Element element, int pageNumber) {
		Element link = findArticleLink(element).orElse(null);
		if (link == null) {
			return SearchCardParseResult.skipped(SearchCardSkipReason.EXTERNAL_OR_INVALID_URL);
		}
		String normalizedUrl = OneNewsAzScraperSupport.normalizePostUrl(firstNonBlank(
				link.attr("data-ctorig"),
				link.attr("href")
		));
		String snippet = text(element, OneNewsAzSelectors.SEARCH_RESULT_SNIPPET);
		OffsetDateTime searchDate = null;
		if (looksLikeOneNewsDate(snippet)) {
			searchDate = dateParser.parseArticleDate(snippet, normalizedUrl).orElse(null);
			if (searchDate == null) {
				return SearchCardParseResult.skipped(SearchCardSkipReason.DATE_PARSE);
			}
		}
		return SearchCardParseResult.card(new OneNewsAzSearchResultCard(
				normalizedUrl,
				normalizeResultTitle(link.text()),
				snippet,
				searchDate,
				null,
				pageNumber
		));
	}

	private Optional<Element> findArticleLink(Element element) {
		return element.select(OneNewsAzSelectors.SEARCH_RESULT_LINK)
				.stream()
				.filter(link -> OneNewsAzScraperSupport.isArticleUrl(firstNonBlank(
						link.attr("data-ctorig"),
						link.attr("href")
				)))
				.findFirst();
	}

	Optional<Integer> nextPageNumber(Document document, Set<Integer> visitedPages) {
		int currentPage = currentPageNumber(document);
		return document.select(OneNewsAzSelectors.CURSOR_PAGE)
				.stream()
				.map(Element::text)
				.map(this::parsePageNumber)
				.flatMap(Optional::stream)
				.filter(pageNumber -> pageNumber > currentPage)
				.filter(pageNumber -> !visitedPages.contains(pageNumber))
				.min(Comparator.naturalOrder());
	}

	private boolean clickNextPage(BrowserPage page, int nextPage) {
		String firstUrlBefore = firstResultUrl(page.content()).orElse(null);
		try {
			page.click(".gsc-cursor-page:has-text(\"" + nextPage + "\")");
			page.waitForTimeout(1_500);
			waitForSearchResults(page);
			Optional<String> firstUrlAfter = firstResultUrl(page.content());
			boolean hasChanged = firstUrlBefore == null || firstUrlAfter.filter(url -> !url.equals(firstUrlBefore)).isPresent();
			if (!hasChanged) {
				log.warn("Stopping 1news.az pagination because page {} did not refresh results", nextPage);
			}
			return hasChanged;
		} catch (BrowserEngineException exception) {
			log.warn("Stopping 1news.az pagination because page {} click failed: {}", nextPage, exception.getMessage());
			return false;
		}
	}

	int currentPageNumber(Document document) {
		return document.select(OneNewsAzSelectors.CURSOR_CURRENT_PAGE)
				.stream()
				.map(Element::text)
				.map(this::parsePageNumber)
				.flatMap(Optional::stream)
				.findFirst()
				.orElse(1);
	}

	private Optional<Integer> parsePageNumber(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Integer.parseInt(value.trim()));
		} catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}

	private Optional<String> firstResultUrl(String html) {
		return firstResultUrl(Jsoup.parse(html, OneNewsAzScraperSupport.ROOT_URL));
	}

	private Optional<String> firstResultUrl(Document document) {
		return document.select(OneNewsAzSelectors.SEARCH_RESULT)
				.stream()
				.map(this::findArticleLink)
				.flatMap(Optional::stream)
				.map(link -> OneNewsAzScraperSupport.normalizePostUrl(firstNonBlank(link.attr("data-ctorig"), link.attr("href"))))
				.findFirst();
	}

	private boolean isSearchUrl(String url) {
		return url != null && url.contains("/az/axtarish/") && url.contains("q=");
	}

	private boolean looksLikeOneNewsDate(String value) {
		return value != null && value.matches(".*\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}\\s*/\\s*\\d{1,2}\\s*/\\s*\\d{4}.*");
	}

	private String normalizeResultTitle(String value) {
		String normalized = normalizeArticleText(value);
		if (normalized == null) {
			return null;
		}
		return normalized.replace(" - 1news.az", "").trim();
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

	private String normalizeText(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
	}

	enum SearchCardSkipReason {
		EXTERNAL_OR_INVALID_URL,
		DATE_PARSE
	}

	private enum ArticleSkipReason {
		EMPTY_TEXT,
		OUT_OF_RANGE,
		INVALID_DATE
	}

	record SearchCardParseResult(
			OneNewsAzSearchResultCard card,
			SearchCardSkipReason skipReason
	) {

		static SearchCardParseResult card(OneNewsAzSearchResultCard card) {
			return new SearchCardParseResult(card, null);
		}

		static SearchCardParseResult skipped(SearchCardSkipReason reason) {
			return new SearchCardParseResult(null, reason);
		}
	}

	record SearchCollectionResult(
			List<OneNewsAzSearchResultCard> cards,
			boolean sortingConfirmed,
			SearchCollectionStats stats,
			String failureMessage
	) {

		static SearchCollectionResult success(
				List<OneNewsAzSearchResultCard> cards,
				boolean sortingConfirmed,
				SearchCollectionStats stats
		) {
			return new SearchCollectionResult(cards, sortingConfirmed, stats, null);
		}

		static SearchCollectionResult failed(String message) {
			return new SearchCollectionResult(List.of(), false, new SearchCollectionStats(), message);
		}
	}

	record SearchCardsResult(
			List<OneNewsAzSearchResultCard> cards,
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
			int articlesOpened,
			int skippedEmptyText,
			int skippedOutOfRange,
			int invalidDates,
			int skippedErrors,
			int articleTimeouts,
			int duplicates
	) {

		private boolean hasExtractionFailures() {
			return skippedEmptyText > 0 || invalidDates > 0 || skippedErrors > 0 || articleTimeouts > 0;
		}

		private ScraperExecutionResult toFailedScraperResult(int candidatesFound) {
			String message = ("1news.az extraction failed: candidatesFound=%d, articlesOpened=%d, "
					+ "articlesSkippedEmptyText=%d, articlesSkippedOutOfRange=%d, "
					+ "articleInvalidDates=%d, articleErrors=%d, articleTimeouts=%d, "
					+ "duplicateArticles=%d, postsCollected=0")
					.formatted(
							candidatesFound,
							articlesOpened,
							skippedEmptyText,
							skippedOutOfRange,
							invalidDates,
							skippedErrors,
							articleTimeouts,
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

	static final class SearchCollectionStats {

		private int searchResultsFound;
		private int externalUrlsSkipped;
		private int duplicatesSkipped;
		private int pagesProcessed;
		private int dateParseFailures;
		private int outOfRangeSkipped;

		int searchResultsFound() {
			return searchResultsFound;
		}

		int externalUrlsSkipped() {
			return externalUrlsSkipped;
		}

		int duplicatesSkipped() {
			return duplicatesSkipped;
		}

		int pagesProcessed() {
			return pagesProcessed;
		}

		int dateParseFailures() {
			return dateParseFailures;
		}

		int outOfRangeSkipped() {
			return outOfRangeSkipped;
		}

		void addSearchResultsFound(int value) {
			searchResultsFound += value;
		}

		void incrementExternalUrlsSkipped() {
			externalUrlsSkipped++;
		}

		void incrementDuplicatesSkipped() {
			duplicatesSkipped++;
		}

		void incrementPagesProcessed() {
			pagesProcessed++;
		}

		void incrementDateParseFailures() {
			dateParseFailures++;
		}

		void incrementOutOfRangeSkipped() {
			outOfRangeSkipped++;
		}
	}

	private static final class PageCollectionStats {

		private final int found;
		private int added;
		private int externalUrlsSkipped;
		private int duplicatesSkipped;
		private int dateParseFailures;
		private int outOfRangeSkipped;
		private int beforeRangeDatedCards;

		private PageCollectionStats(int found) {
			this.found = found;
		}

		int found() {
			return found;
		}

		int added() {
			return added;
		}

		int externalUrlsSkipped() {
			return externalUrlsSkipped;
		}

		int duplicatesSkipped() {
			return duplicatesSkipped;
		}

		int dateParseFailures() {
			return dateParseFailures;
		}

		int outOfRangeSkipped() {
			return outOfRangeSkipped;
		}

		void incrementAdded() {
			added++;
		}

		void incrementExternalUrlsSkipped() {
			externalUrlsSkipped++;
		}

		void incrementDuplicatesSkipped() {
			duplicatesSkipped++;
		}

		void incrementDateParseFailures() {
			dateParseFailures++;
		}

		void incrementOutOfRangeSkipped() {
			outOfRangeSkipped++;
		}

		void incrementBeforeRangeDatedCards() {
			beforeRangeDatedCards++;
		}

		boolean onlyAcceptedDatedCardsAreOld() {
			return beforeRangeDatedCards > 0 && added == 0 && externalUrlsSkipped < found;
		}
	}
}
