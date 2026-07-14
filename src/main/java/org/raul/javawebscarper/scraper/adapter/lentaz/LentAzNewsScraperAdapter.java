package org.raul.javawebscarper.scraper.adapter.lentaz;

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
import org.raul.javawebscarper.scraper.support.DateRangeValidator;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;
import org.springframework.stereotype.Component;

import java.net.URI;
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
public class LentAzNewsScraperAdapter implements NewsScraperAdapter {

	private static final int MIN_KEYWORD_LENGTH = 3;
	private static final int MIN_ARTICLE_TEXT_LENGTH = 50;
	private static final int MIN_PARAGRAPH_TEXT_LENGTH = 15;
	private static final int MAX_CONSECUTIVE_PAGES_WITHOUT_NEW_POSTS = 2;
	private static final String ARTICLE_CLEANUP_SELECTOR = String.join(", ",
			"script",
			"style",
			"template",
			"iframe",
			"ins",
			".reaction-wrap",
			".actions_item",
			".emoji-container",
			".emojies_news",
			".main_emojis",
			".news_img",
			".news_info",
			"[class*=banner]",
			"[class*=reklam]",
			"[class*=advert]",
			"[class*=related]",
			"[class*=similar]",
			"[class*=emoji]",
			"[id*=banner]"
	);
	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"lent.az",
			"lent.az",
			"Lent.az",
			LentAzScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final LentAzDateParser dateParser;
	private final LentAzSearchPeriodResolver searchPeriodResolver;

	@Override
	public String sourceCode() {
		return LentAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return LentAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("lent.az search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		int searchType = searchPeriodResolver.resolve(context.dateFrom(), context.dateTo());
		log.info(
				"Starting lent.az scraping: source={}, keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}, selectedSearchType={}",
				context.source().getCode(),
				keyword,
				context.dateFrom(),
				context.dateTo(),
				context.maxPages(),
				context.maxPosts(),
				searchType
		);

		try (BrowserSession session = browserSessionFactory.createSession()) {
			BrowserPage page = session.newPage();
			SearchCollectionResult searchResult = openAndCollectSearchResults(page, keyword.trim(), searchType, context);
			if (searchResult.failureMessage() != null) {
				return ScraperExecutionResult.failed(searchResult.failureMessage());
			}
			log.info(
					"Finished lent.az search collection: selectedSearchType={}, searchCardsFound={}, adsSkipped={}, "
							+ "acceptedUrls={}, duplicateUrlsSkipped={}, invalidUrlsSkipped={}, pagesProcessed={}, "
							+ "paginationFailures={}, tooNewSkipped={}, tooOldSkipped={}",
					searchType,
					searchResult.stats().searchCardsFound(),
					searchResult.stats().adCardsSkipped(),
					searchResult.cards().size(),
					searchResult.stats().duplicateUrlsSkipped(),
					searchResult.stats().invalidUrlsSkipped(),
					searchResult.stats().pagesProcessed(),
					searchResult.stats().paginationFailures(),
					searchResult.stats().tooNewSkipped(),
					searchResult.stats().tooOldSkipped()
			);

			ArticleCollectionResult articleResult = collectArticles(page, searchResult.cards(), searchType, context);
			log.info(
					"Finished lent.az scraping: selectedSearchType={}, cardsFound={}, adsSkipped={}, pagesProcessed={}, "
							+ "articlesOpened={}, postsCollected={}, articleTimeouts={}, articleRootNotFound={}, "
							+ "dateParseFailures={}, tooNewSkipped={}, tooOldSkipped={}, emptyTextSkipped={}, "
							+ "mainImagesCollected={}, articleErrors={}",
					searchType,
					searchResult.stats().searchCardsFound(),
					searchResult.stats().adCardsSkipped(),
					searchResult.stats().pagesProcessed(),
					articleResult.articlePagesOpened(),
					articleResult.posts().size(),
					articleResult.articleTimeouts(),
					articleResult.articleRootNotFound(),
					articleResult.dateParseFailures(),
					searchResult.stats().tooNewSkipped() + articleResult.tooNewSkipped(),
					searchResult.stats().tooOldSkipped() + articleResult.tooOldSkipped(),
					articleResult.emptyTextSkipped(),
					articleResult.mainImagesCollected(),
					articleResult.articleErrors()
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
			log.warn("lent.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("lent.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected lent.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected lent.az scraping failure: " + exception.getMessage());
		}
	}

	private SearchCollectionResult openAndCollectSearchResults(
			BrowserPage page,
			String keyword,
			int searchType,
			ScraperExecutionContext context
	) {
		if (!openSearchPage(page, keyword, searchType)) {
			String message = "lent.az search page did not load";
			log.warn(message);
			return SearchCollectionResult.failed(message);
		}
		SearchCardsResult cardsResult = collectSearchResultCards(page, keyword, searchType, context);
		return SearchCollectionResult.success(cardsResult.cards(), cardsResult.stats());
	}

	private boolean openSearchPage(BrowserPage page, String keyword, int searchType) {
		String fallbackUrl = searchUrl(keyword, searchType);
		try {
			log.info("Opening lent.az search through UI flow: keyword={}, type={}", keyword, searchType);
			page.navigate(LentAzScraperSupport.BASE_URL);
			page.waitForSelector(LentAzSelectors.SEARCH_OPEN_BUTTON, 5_000);
			page.click(LentAzSelectors.SEARCH_OPEN_BUTTON);
			waitForSearchForm(page);
			fillSearchInput(page, keyword);
			selectSearchType(page, searchType);
			clickSubmit(page);
			page.waitForTimeout(1_000);
			if (!isSearchUrl(page.url())) {
				log.warn("lent.az UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			}
		} catch (BrowserEngineException exception) {
			log.warn("lent.az UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
		}
		boolean loaded = waitForSearchPage(page);
		log.info("lent.az search page opened: url={}, loaded={}, type={}", page.url(), loaded, searchType);
		return loaded;
	}

	private void waitForSearchForm(BrowserPage page) {
		try {
			page.waitForSelector(LentAzSelectors.SEARCH_FORM, 5_000);
		} catch (BrowserEngineException exception) {
			page.waitForSelector(LentAzSelectors.SEARCH_INPUT_FALLBACK, 5_000);
		}
	}

	private void fillSearchInput(BrowserPage page, String keyword) {
		for (String selector : List.of(LentAzSelectors.SEARCH_INPUT, LentAzSelectors.SEARCH_INPUT_FALLBACK)) {
			try {
				page.fill(selector, "");
				page.fill(selector, keyword);
				return;
			} catch (BrowserEngineException exception) {
				log.debug("lent.az search input selector failed: selector={}", selector);
			}
		}
		throw new BrowserEngineException("lent.az search input was not found");
	}

	private void selectSearchType(BrowserPage page, int searchType) {
		try {
			page.click(LentAzSelectors.SEARCH_TYPE_SELECT);
			page.press(LentAzSelectors.SEARCH_TYPE_SELECT, "Home");
			for (int index = 1; index < searchType; index++) {
				page.press(LentAzSelectors.SEARCH_TYPE_SELECT, "ArrowDown");
			}
			page.press(LentAzSelectors.SEARCH_TYPE_SELECT, "Enter");
		} catch (BrowserEngineException exception) {
			log.warn("lent.az search type selection failed, fallback URL will enforce type={}: {}", searchType, exception.getMessage());
			throw exception;
		}
	}

	private void clickSubmit(BrowserPage page) {
		try {
			page.click(LentAzSelectors.SEARCH_SUBMIT_BUTTON);
		} catch (BrowserEngineException exception) {
			page.click(LentAzSelectors.SEARCH_SUBMIT_BUTTON_FALLBACK);
		}
	}

	private boolean waitForSearchPage(BrowserPage page) {
		try {
			page.waitForSelector("body", 10_000);
			try {
				page.waitForSelector(LentAzSelectors.SEARCH_RESULT_CARD, 5_000);
			} catch (BrowserTimeoutException exception) {
				log.debug("lent.az primary result cards did not appear before timeout: url={}", page.url());
			}
			return isSearchUrl(page.url());
		} catch (BrowserTimeoutException exception) {
			return false;
		}
	}

	String searchUrl(String keyword, int searchType) {
		return LentAzScraperSupport.BASE_URL + "axtaris-neticesi?search="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8)
				+ "&type=" + searchType;
	}

	SearchCardsResult collectSearchResultCards(
			BrowserPage page,
			String keyword,
			int searchType,
			ScraperExecutionContext context
	) {
		Map<String, LentAzSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();
		Set<String> visitedPageUrls = new LinkedHashSet<>();
		Set<Integer> visitedPageNumbers = new LinkedHashSet<>();
		SearchCollectionStats stats = new SearchCollectionStats();
		stats.setSelectedSearchType(searchType);
		int consecutivePagesWithoutNewPosts = 0;
		int consecutivePagesOnlyOld = 0;

		for (int pageIndex = 0; pageIndex < context.maxPages() && cardsByUrl.size() < context.maxPosts(); pageIndex++) {
			Document document = Jsoup.parse(page.content(), LentAzScraperSupport.BASE_URL);
			String normalizedPageUrl = normalizePageUrl(page.url());
			int currentPage = currentPageNumber(page.url(), document);
			if (!visitedPageUrls.add(normalizedPageUrl) || !visitedPageNumbers.add(currentPage)) {
				log.warn("Stopping lent.az pagination because page was already visited: page={}, url={}", currentPage, normalizedPageUrl);
				break;
			}
			stats.incrementPagesProcessed();
			PageCollectionStats pageStats = collectCurrentPageCards(document, currentPage, context, cardsByUrl, seenUrls, stats);
			log.info(
					"lent.az search page={} stats: found={}, adsSkipped={}, added={}, duplicates={}, invalidUrls={}, tooNew={}, tooOld={}",
					currentPage,
					pageStats.found(),
					pageStats.adsSkipped(),
					pageStats.added(),
					pageStats.duplicates(),
					pageStats.invalidUrls(),
					pageStats.tooNew(),
					pageStats.tooOld()
			);

			if (cardsByUrl.size() >= context.maxPosts() || pageIndex + 1 >= context.maxPages()) {
				break;
			}
			if (pageStats.added() == 0) {
				consecutivePagesWithoutNewPosts++;
				if (consecutivePagesWithoutNewPosts >= MAX_CONSECUTIVE_PAGES_WITHOUT_NEW_POSTS) {
					log.info("Stopping lent.az pagination after {} pages without new posts", consecutivePagesWithoutNewPosts);
					break;
				}
			} else {
				consecutivePagesWithoutNewPosts = 0;
			}
			if (pageStats.onlyNewDatedCardsAreOld()) {
				consecutivePagesOnlyOld++;
				if (consecutivePagesOnlyOld >= MAX_CONSECUTIVE_PAGES_WITHOUT_NEW_POSTS) {
					log.info("Stopping lent.az pagination after {} pages older than dateFrom", consecutivePagesOnlyOld);
					break;
				}
			} else {
				consecutivePagesOnlyOld = 0;
			}
			Optional<String> nextPageUrl = nextPageUrl(document, page.url(), keyword, searchType, visitedPageUrls, visitedPageNumbers);
			if (nextPageUrl.isEmpty()) {
				break;
			}
			if (!openNextPage(page, nextPageUrl.get(), seenUrls, stats)) {
				break;
			}
		}
		if (!cardsByUrl.isEmpty()) {
			log.info("lent.az candidate URLs: {}", cardsByUrl.keySet().stream().limit(10).toList());
		}
		return new SearchCardsResult(cardsByUrl.values().stream().limit(context.maxPosts()).toList(), stats);
	}

	private PageCollectionStats collectCurrentPageCards(
			Document document,
			int currentPage,
			ScraperExecutionContext context,
			Map<String, LentAzSearchResultCard> cardsByUrl,
			Set<String> seenUrls,
			SearchCollectionStats totalStats
	) {
		Elements elements = document.select(LentAzSelectors.SEARCH_RESULT_CARD);
		if (elements.isEmpty()) {
			elements = document.select(LentAzSelectors.SEARCH_RESULT_CARD_FALLBACK)
					.stream()
					.filter(element -> element.selectFirst(LentAzSelectors.SEARCH_RESULT_LINK_ANY) != null)
					.collect(Elements::new, Elements::add, Elements::addAll);
		}
		PageCollectionStats stats = new PageCollectionStats(elements.size());
		totalStats.addSearchCardsFound(elements.size());
		for (Element element : elements) {
			SearchCardParseResult parsedResult = parseResultCard(element, currentPage);
			if (parsedResult.skipReason() == SearchCardSkipReason.AD) {
				stats.incrementAdsSkipped();
				totalStats.incrementAdCardsSkipped();
				continue;
			}
			if (parsedResult.card() == null) {
				stats.incrementInvalidUrls();
				totalStats.incrementInvalidUrlsSkipped();
				continue;
			}
			LentAzSearchResultCard card = parsedResult.card();
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

	SearchCardParseResult parseResultCard(Element element, int searchPageNumber) {
		if (isAdCard(element)) {
			return SearchCardParseResult.skipped(SearchCardSkipReason.AD);
		}
		Element link = firstElement(
				element.selectFirst(LentAzSelectors.SEARCH_RESULT_LINK_OVERLAY),
				element.selectFirst(LentAzSelectors.SEARCH_RESULT_LINK_TITLE),
				element.selectFirst(LentAzSelectors.SEARCH_RESULT_LINK_ANY)
		);
		if (link == null) {
			return SearchCardParseResult.skipped(SearchCardSkipReason.INVALID_URL);
		}
		String normalizedUrl = LentAzScraperSupport.normalizePostUrl(link.attr("href")).orElse(null);
		if (normalizedUrl == null) {
			return SearchCardParseResult.skipped(SearchCardSkipReason.INVALID_URL);
		}
		String externalPostId = LentAzScraperSupport.extractExternalPostId(element.attr("data-id"), normalizedUrl)
				.orElseGet(() -> Integer.toHexString(normalizedUrl.hashCode()));
		OffsetDateTime searchDate = parseSearchCardDate(element, normalizedUrl).orElse(null);
		String thumbnailUrl = Optional.ofNullable(element.selectFirst(LentAzSelectors.SEARCH_RESULT_THUMBNAIL))
				.map(image -> image.attr("src"))
				.filter(LentAzScraperSupport::isAllowedMediaUrl)
				.map(rawUrl -> UrlNormalizer.resolve(normalizedUrl, rawUrl))
				.map(LentAzScraperSupport::forceHttps)
				.filter(LentAzScraperSupport::isAllowedMediaUrl)
				.orElse(null);
		return SearchCardParseResult.card(new LentAzSearchResultCard(
				normalizedUrl,
				externalPostId,
				firstNonBlank(text(element, LentAzSelectors.SEARCH_RESULT_TITLE), link.text()),
				searchDate,
				thumbnailUrl,
				searchPageNumber
		));
	}

	private Optional<OffsetDateTime> parseSearchCardDate(Element element, String postUrl) {
		List<String> spans = element.select(LentAzSelectors.SEARCH_RESULT_DATE_SPAN)
				.stream()
				.map(Element::text)
				.map(this::normalizeArticleText)
				.filter(text -> text != null && !text.isBlank())
				.toList();
		String timeText = spans.stream().filter(dateParser::isTimeText).findFirst().orElse(null);
		String dateText = spans.stream().filter(value -> !dateParser.isTimeText(value)).findFirst().orElse(null);
		return dateParser.parseSearchCardDate(dateText, timeText, postUrl);
	}

	Optional<String> nextPageUrl(
			Document document,
			String currentUrl,
			String keyword,
			int searchType,
			Set<String> visitedPageUrls,
			Set<Integer> visitedPageNumbers
	) {
		int currentPage = currentPageNumber(currentUrl, document);
		Optional<String> relNext = document.select(LentAzSelectors.PAGINATION_NEXT_LINK)
				.stream()
				.map(link -> normalizePageUrl(UrlNormalizer.resolve(currentUrl, link.attr("href"))))
				.filter(url -> !url.equals(normalizePageUrl(currentUrl)))
				.filter(url -> !visitedPageUrls.contains(url))
				.filter(url -> currentPageNumber(url, document) > currentPage)
				.findFirst();
		if (relNext.isPresent()) {
			return relNext;
		}
		int nextPage = currentPage + 1;
		Optional<String> numberedLink = document.select(LentAzSelectors.PAGINATION_NUMBERED_LINK)
				.stream()
				.filter(link -> parsePageNumber(link.text()).filter(pageNumber -> pageNumber == nextPage).isPresent())
				.map(link -> normalizePageUrl(UrlNormalizer.resolve(currentUrl, link.attr("href"))))
				.filter(url -> !visitedPageUrls.contains(url))
				.findFirst();
		if (numberedLink.isPresent() && !visitedPageNumbers.contains(nextPage)) {
			return numberedLink;
		}
		if (document.selectFirst(LentAzSelectors.PAGINATION_ROOT) == null || visitedPageNumbers.contains(nextPage)) {
			return Optional.empty();
		}
		return Optional.of(searchUrl(keyword, searchType) + "&page=" + nextPage)
				.map(this::normalizePageUrl)
				.filter(url -> !visitedPageUrls.contains(url));
	}

	private boolean openNextPage(BrowserPage page, String nextPageUrl, Set<String> alreadySeenPostUrls, SearchCollectionStats stats) {
		Set<String> beforeUrls = collectPostUrls(Jsoup.parse(page.content(), LentAzScraperSupport.BASE_URL));
		try {
			page.navigate(nextPageUrl);
			page.waitForTimeout(1_000);
			waitForSearchPage(page);
			Set<String> afterUrls = collectPostUrls(Jsoup.parse(page.content(), LentAzScraperSupport.BASE_URL));
			boolean hasNewUrl = afterUrls.stream()
					.anyMatch(url -> !beforeUrls.contains(url) && !alreadySeenPostUrls.contains(url));
			if (!hasNewUrl) {
				log.warn("Stopping lent.az pagination because next page has no new URLs: url={}", nextPageUrl);
			}
			return hasNewUrl;
		} catch (BrowserEngineException exception) {
			stats.incrementPaginationFailures();
			log.warn("Stopping lent.az pagination because next page failed: url={}, error={}", nextPageUrl, exception.getMessage());
			return false;
		}
	}

	private Set<String> collectPostUrls(Document document) {
		Set<String> urls = new LinkedHashSet<>();
		document.select(LentAzSelectors.SEARCH_RESULT_LINK_ANY)
				.stream()
				.map(link -> LentAzScraperSupport.normalizePostUrl(link.attr("href")))
				.flatMap(Optional::stream)
				.forEach(urls::add);
		return urls;
	}

	private ArticleCollectionResult collectArticles(
			BrowserPage page,
			List<LentAzSearchResultCard> cards,
			int searchType,
			ScraperExecutionContext context
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenExternalIds = new LinkedHashSet<>();
		Set<String> seenPostUrls = new LinkedHashSet<>();
		ArticleCounters counters = new ArticleCounters();

		for (LentAzSearchResultCard card : cards) {
			if (posts.size() >= context.maxPosts()) {
				break;
			}
			counters.articlePagesOpened++;
			try {
				ArticleCollectionAttempt attempt = collectArticleWithRetry(page, card, searchType, context);
				if (attempt.post() == null) {
					counters.incrementSkip(attempt.skipReason());
					continue;
				}
				ScrapedPostDTO post = attempt.post();
				if (seenExternalIds.add(post.externalPostId()) && seenPostUrls.add(post.postUrl())) {
					counters.mainImagesCollected += attempt.mainImagesCollected();
					posts.add(post);
				} else {
					counters.duplicates++;
					log.debug("Skipping duplicate lent.az article: url={}, externalPostId={}", post.postUrl(), post.externalPostId());
				}
			} catch (BrowserTimeoutException exception) {
				counters.articleTimeouts++;
				log.warn("Skipping lent.az article after timeout: url={}", card.postUrl());
			} catch (RuntimeException exception) {
				counters.articleErrors++;
				log.warn("Skipping lent.az article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return counters.toResult(posts);
	}

	private ArticleCollectionAttempt collectArticleWithRetry(
			BrowserPage page,
			LentAzSearchResultCard card,
			int searchType,
			ScraperExecutionContext context
	) {
		try {
			return collectArticle(page, card, searchType, context);
		} catch (BrowserTimeoutException exception) {
			log.warn("Timeout opening lent.az article, retrying once: url={}", card.postUrl());
			return collectArticle(page, card, searchType, context);
		}
	}

	private ArticleCollectionAttempt collectArticle(
			BrowserPage page,
			LentAzSearchResultCard card,
			int searchType,
			ScraperExecutionContext context
	) {
		page.navigate(card.postUrl());
		page.waitForTimeout(500);
		Document document = Jsoup.parse(page.content(), card.postUrl());
		Element root = document.selectFirst(LentAzSelectors.ARTICLE_ROOT);
		if (root == null) {
			log.warn("Skipping lent.az article because article root was not found: url={}", card.postUrl());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.ARTICLE_ROOT_NOT_FOUND);
		}
		OffsetDateTime postDate = parseArticleDate(root, card).orElse(null);
		if (postDate == null) {
			log.warn("Skipping lent.az article with missing or invalid date: url={}", card.postUrl());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.INVALID_DATE);
		}
		if (DateRangeValidator.isAfterRange(postDate, context.dateTo())) {
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.TOO_NEW);
		}
		if (DateRangeValidator.isBeforeRange(postDate, context.dateFrom())) {
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.TOO_OLD);
		}

		String text = extractArticleText(root);
		if (text == null || text.isBlank()) {
			log.warn("Skipping lent.az article with empty text: url={}, title={}", card.postUrl(), card.title());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.EMPTY_TEXT);
		}

		String externalPostId = firstNonBlank(card.externalPostId(), LentAzScraperSupport.extractExternalPostId(null, card.postUrl()).orElse(null));
		String title = firstNonBlank(text(root, LentAzSelectors.ARTICLE_TITLE), card.title());
		MediaExtractionResult mediaResult = extractMedia(root, card);
		Map<String, Object> metadata = metadata(context, card, searchType, title);

		log.info("Collected lent.az article: url={}, mediaCount={}", card.postUrl(), mediaResult.media().size());
		return ArticleCollectionAttempt.collected(
				new ScrapedPostDTO(
						externalPostId,
						card.postUrl(),
						postDate,
						AUTHOR,
						text,
						"az",
						mediaResult.media(),
						metadata
				),
				mediaResult.mainImagesCollected()
		);
	}

	Optional<OffsetDateTime> parseArticleDate(Element root, LentAzSearchResultCard card) {
		String articleDate = text(root, LentAzSelectors.ARTICLE_DATE);
		Optional<OffsetDateTime> parsedArticleDate = dateParser.parseArticleDate(articleDate, card.postUrl());
		return parsedArticleDate.isPresent() ? parsedArticleDate : Optional.ofNullable(card.searchDate());
	}

	String extractArticleText(Element root) {
		Optional<Element> textRoot = findArticleTextRoot(root);
		if (textRoot.isEmpty()) {
			return null;
		}
		Element cleanRoot = textRoot.get().clone();
		cleanRoot.select(ARTICLE_CLEANUP_SELECTOR).remove();
		String text = cleanRoot.select("p")
				.stream()
				.map(Element::text)
				.map(this::normalizeArticleText)
				.filter(this::isArticleParagraph)
				.reduce((left, right) -> left + "\n" + right)
				.orElse(null);
		return isUsableArticleText(text) ? text : null;
	}

	Optional<Element> findArticleTextRoot(Element articleRoot) {
		return articleRoot.select(LentAzSelectors.ARTICLE_TEXT_CANDIDATE)
				.stream()
				.filter(candidate -> !candidate.hasClass("news_info"))
				.max(Comparator.comparingInt(this::paragraphTextLength))
				.filter(candidate -> paragraphTextLength(candidate) >= MIN_ARTICLE_TEXT_LENGTH)
				.or(() -> Optional.of(cleanedArticleRoot(articleRoot))
						.filter(candidate -> paragraphTextLength(candidate) >= MIN_ARTICLE_TEXT_LENGTH));
	}

	private Element cleanedArticleRoot(Element articleRoot) {
		Element cleanRoot = articleRoot.clone();
		cleanRoot.select(ARTICLE_CLEANUP_SELECTOR).remove();
		return cleanRoot;
	}

	private int paragraphTextLength(Element element) {
		return element.select("p")
				.stream()
				.map(Element::text)
				.map(this::normalizeArticleText)
				.filter(this::isArticleParagraph)
				.mapToInt(String::length)
				.sum();
	}

	private boolean isArticleParagraph(String text) {
		if (text == null || text.length() < MIN_PARAGRAPH_TEXT_LENGTH) {
			return false;
		}
		String normalized = text.toLowerCase(Locale.ROOT);
		return !normalized.contains("total_count")
				&& !normalized.contains("emoji-container")
				&& !normalized.contains("reaction-wrap")
				&& !normalized.contains("newmedia.az")
				&& !normalized.contains("reklam")
				&& !normalized.contains("banner");
	}

	private boolean isUsableArticleText(String text) {
		return text != null && text.length() >= MIN_ARTICLE_TEXT_LENGTH;
	}

	MediaExtractionResult extractMedia(Element root, LentAzSearchResultCard card) {
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		int mainImagesCollected = 0;
		Element mainImage = firstElement(
				root.selectFirst(LentAzSelectors.ARTICLE_MAIN_IMAGE),
				root.selectFirst(LentAzSelectors.ARTICLE_MAIN_IMAGE_FALLBACK)
		);
		if (mainImage != null) {
			String rawUrl = mainImage.attr("src");
			if (LentAzScraperSupport.isAllowedMediaUrl(rawUrl)) {
				String normalizedUrl = LentAzScraperSupport.forceHttps(UrlNormalizer.resolve(card.postUrl(), rawUrl));
				if (LentAzScraperSupport.isAllowedMediaUrl(normalizedUrl)) {
					mediaByUrl.put(normalizedUrl, MediaType.IMAGE);
					mainImagesCollected = 1;
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
		return new MediaExtractionResult(media, mainImagesCollected);
	}

	private Map<String, Object> metadata(ScraperExecutionContext context, LentAzSearchResultCard card, int searchType, String title) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", LentAzScraperSupport.SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		if (title != null && !title.isBlank()) {
			metadata.put("title", title);
		}
		metadata.put("searchType", searchType);
		metadata.put("searchPageNumber", card.searchPageNumber());
		return metadata;
	}

	int currentPageNumber(String currentUrl, Document document) {
		return pageNumberFromUrl(currentUrl)
				.or(() -> Optional.ofNullable(document.selectFirst(LentAzSelectors.PAGINATION_CURRENT))
						.map(Element::text)
						.flatMap(this::parsePageNumber))
				.orElse(1);
	}

	private Optional<Integer> pageNumberFromUrl(String currentUrl) {
		if (currentUrl == null || currentUrl.isBlank()) {
			return Optional.empty();
		}
		try {
			String query = URI.create(currentUrl).getRawQuery();
			if (query == null || query.isBlank()) {
				return Optional.empty();
			}
			for (String parameter : query.split("&")) {
				String[] parts = parameter.split("=", 2);
				if (parts.length == 2 && "page".equalsIgnoreCase(parts[0])) {
					return parsePageNumber(parts[1]);
				}
			}
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
		return Optional.empty();
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

	private boolean isSearchUrl(String url) {
		return url != null && url.toLowerCase(Locale.ROOT).contains("/axtaris-neticesi");
	}

	private boolean isAdCard(Element element) {
		return element.is(LentAzSelectors.AD_CARD)
				|| element.selectFirst(".rek_desktop, .rek_baner_mobile, .custom-banner, script[src*=newmedia.az]") != null;
	}

	private String normalizePageUrl(String url) {
		return LentAzScraperSupport.forceHttps(UrlNormalizer.removeTrackingParams(url));
	}

	private String normalizeArticleText(String value) {
		if (value == null) {
			return null;
		}
		return value.replace('\u00A0', ' ')
				.replaceAll("[ \\t]+", " ")
				.replaceAll("\\s*\\n\\s*", "\n")
				.trim();
	}

	private static Element firstElement(Element... elements) {
		for (Element element : elements) {
			if (element != null) {
				return element;
			}
		}
		return null;
	}

	private static String text(Element element, String selector) {
		Element selected = element.selectFirst(selector);
		return selected == null ? null : selected.text().replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	record SearchCardParseResult(LentAzSearchResultCard card, SearchCardSkipReason skipReason) {

		static SearchCardParseResult card(LentAzSearchResultCard card) {
			return new SearchCardParseResult(card, null);
		}

		static SearchCardParseResult skipped(SearchCardSkipReason reason) {
			return new SearchCardParseResult(null, reason);
		}
	}

	private enum SearchCardSkipReason {
		AD,
		INVALID_URL
	}

	record SearchCollectionResult(
			List<LentAzSearchResultCard> cards,
			SearchCollectionStats stats,
			String failureMessage
	) {

		static SearchCollectionResult success(List<LentAzSearchResultCard> cards, SearchCollectionStats stats) {
			return new SearchCollectionResult(cards, stats, null);
		}

		static SearchCollectionResult failed(String message) {
			return new SearchCollectionResult(List.of(), new SearchCollectionStats(), message);
		}
	}

	record SearchCardsResult(List<LentAzSearchResultCard> cards, SearchCollectionStats stats) {
	}

	static final class PageCollectionStats {

		private final int found;
		private int adsSkipped;
		private int added;
		private int duplicates;
		private int invalidUrls;
		private int tooNew;
		private int tooOld;
		private int newDatedCards;

		PageCollectionStats(int found) {
			this.found = found;
		}

		int found() {
			return found;
		}

		int adsSkipped() {
			return adsSkipped;
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

		int tooNew() {
			return tooNew;
		}

		int tooOld() {
			return tooOld;
		}

		boolean onlyNewDatedCardsAreOld() {
			return newDatedCards > 0 && newDatedCards == tooOld;
		}

		void incrementAdsSkipped() {
			adsSkipped++;
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

		void incrementTooNew() {
			tooNew++;
		}

		void incrementTooOld() {
			tooOld++;
		}

		void incrementNewDatedCards() {
			newDatedCards++;
		}
	}

	static final class SearchCollectionStats {

		private int selectedSearchType;
		private int searchCardsFound;
		private int adCardsSkipped;
		private int duplicateUrlsSkipped;
		private int invalidUrlsSkipped;
		private int pagesProcessed;
		private int paginationFailures;
		private int tooNewSkipped;
		private int tooOldSkipped;

		int selectedSearchType() {
			return selectedSearchType;
		}

		int searchCardsFound() {
			return searchCardsFound;
		}

		int adCardsSkipped() {
			return adCardsSkipped;
		}

		int duplicateUrlsSkipped() {
			return duplicateUrlsSkipped;
		}

		int invalidUrlsSkipped() {
			return invalidUrlsSkipped;
		}

		int pagesProcessed() {
			return pagesProcessed;
		}

		int paginationFailures() {
			return paginationFailures;
		}

		int tooNewSkipped() {
			return tooNewSkipped;
		}

		int tooOldSkipped() {
			return tooOldSkipped;
		}

		void setSelectedSearchType(int selectedSearchType) {
			this.selectedSearchType = selectedSearchType;
		}

		void addSearchCardsFound(int count) {
			searchCardsFound += count;
		}

		void incrementAdCardsSkipped() {
			adCardsSkipped++;
		}

		void incrementDuplicateUrlsSkipped() {
			duplicateUrlsSkipped++;
		}

		void incrementInvalidUrlsSkipped() {
			invalidUrlsSkipped++;
		}

		void incrementPagesProcessed() {
			pagesProcessed++;
		}

		void incrementPaginationFailures() {
			paginationFailures++;
		}

		void incrementTooNewSkipped() {
			tooNewSkipped++;
		}

		void incrementTooOldSkipped() {
			tooOldSkipped++;
		}
	}

	record ArticleCollectionAttempt(ScrapedPostDTO post, ArticleSkipReason skipReason, int mainImagesCollected) {

		static ArticleCollectionAttempt collected(ScrapedPostDTO post, int mainImagesCollected) {
			return new ArticleCollectionAttempt(post, null, mainImagesCollected);
		}

		static ArticleCollectionAttempt skipped(ArticleSkipReason reason) {
			return new ArticleCollectionAttempt(null, reason, 0);
		}
	}

	record ArticleCollectionResult(
			List<ScrapedPostDTO> posts,
			int articlePagesOpened,
			int articleTimeouts,
			int articleRootNotFound,
			int dateParseFailures,
			int tooNewSkipped,
			int tooOldSkipped,
			int emptyTextSkipped,
			int duplicates,
			int mainImagesCollected,
			int articleErrors
	) {

		boolean hasExtractionFailures() {
			return articleTimeouts > 0
					|| articleRootNotFound > 0
					|| dateParseFailures > 0
					|| emptyTextSkipped > 0
					|| articleErrors > 0;
		}

		ScraperExecutionResult toFailedScraperResult(int candidatesFound) {
			String message = ("lent.az extraction failed: candidatesFound=%d, articlePagesOpened=%d, "
					+ "articleTimeouts=%d, articleRootNotFound=%d, dateParseFailures=%d, emptyTextSkipped=%d, errors=%d")
					.formatted(
							candidatesFound,
							articlePagesOpened,
							articleTimeouts,
							articleRootNotFound,
							dateParseFailures,
							emptyTextSkipped,
							articleErrors
					);
			return ScraperExecutionResult.failed(message);
		}
	}

	record MediaExtractionResult(List<ScrapedMediaDTO> media, int mainImagesCollected) {
	}

	private static final class ArticleCounters {

		private int articlePagesOpened;
		private int articleTimeouts;
		private int articleRootNotFound;
		private int dateParseFailures;
		private int tooNewSkipped;
		private int tooOldSkipped;
		private int emptyTextSkipped;
		private int duplicates;
		private int mainImagesCollected;
		private int articleErrors;

		void incrementSkip(ArticleSkipReason reason) {
			if (reason == null) {
				return;
			}
			switch (reason) {
				case ARTICLE_ROOT_NOT_FOUND -> articleRootNotFound++;
				case INVALID_DATE -> dateParseFailures++;
				case TOO_NEW -> tooNewSkipped++;
				case TOO_OLD -> tooOldSkipped++;
				case EMPTY_TEXT -> emptyTextSkipped++;
			}
		}

		ArticleCollectionResult toResult(List<ScrapedPostDTO> posts) {
			return new ArticleCollectionResult(
					posts,
					articlePagesOpened,
					articleTimeouts,
					articleRootNotFound,
					dateParseFailures,
					tooNewSkipped,
					tooOldSkipped,
					emptyTextSkipped,
					duplicates,
					mainImagesCollected,
					articleErrors
			);
		}
	}

	private enum ArticleSkipReason {
		ARTICLE_ROOT_NOT_FOUND,
		INVALID_DATE,
		TOO_NEW,
		TOO_OLD,
		EMPTY_TEXT
	}
}
