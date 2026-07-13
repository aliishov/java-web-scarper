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
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.NewsScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
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
			return ScraperExecutionResult.empty();
		} catch (BrowserEngineException exception) {
			log.warn("1news.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("1news.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected 1news.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected 1news.az scraping failure: " + exception.getMessage());
		}
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
