package org.raul.javawebscarper.scraper.adapter.qafqazinfoaz;

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
public class QafqazInfoAzNewsScraperAdapter implements NewsScraperAdapter {

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
			".social-buttons",
			".visible-xs",
			".hidden-xs",
			"[class*=banner]",
			"[id*=banner]"
	);
	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"qafqazinfo.az",
			"qafqazinfo.az",
			"Qafqazinfo.az",
			QafqazInfoAzScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final QafqazInfoAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return QafqazInfoAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return QafqazInfoAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("qafqazinfo.az search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		log.info(
				"Starting qafqazinfo.az scraping: source={}, keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
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
					"Finished qafqazinfo.az search collection: searchCardsFound={}, acceptedUrls={}, "
							+ "duplicateUrlsSkipped={}, invalidUrlsSkipped={}, pagesProcessed={}, paginationFailures={}",
					searchResult.stats().searchCardsFound(),
					searchResult.cards().size(),
					searchResult.stats().duplicateUrlsSkipped(),
					searchResult.stats().invalidUrlsSkipped(),
					searchResult.stats().pagesProcessed(),
					searchResult.stats().paginationFailures()
			);

			ArticleCollectionResult articleResult = collectArticles(page, searchResult.cards(), context);
			log.info(
					"Finished qafqazinfo.az scraping: cardsFound={}, pagesProcessed={}, articlesOpened={}, "
							+ "postsCollected={}, articleTimeouts={}, articleRootNotFound={}, dateParseFailures={}, "
							+ "dateSourceConflicts={}, tooNewSkipped={}, tooOldSkipped={}, emptyTextSkipped={}, "
							+ "mainImagesCollected={}, articleErrors={}",
					searchResult.stats().searchCardsFound(),
					searchResult.stats().pagesProcessed(),
					articleResult.articlePagesOpened(),
					articleResult.posts().size(),
					articleResult.articleTimeouts(),
					articleResult.articleRootNotFound(),
					articleResult.dateParseFailures(),
					articleResult.dateSourceConflicts(),
					articleResult.tooNewSkipped(),
					articleResult.tooOldSkipped(),
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
			log.warn("qafqazinfo.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("qafqazinfo.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected qafqazinfo.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected qafqazinfo.az scraping failure: " + exception.getMessage());
		}
	}

	private SearchCollectionResult openAndCollectSearchResults(
			BrowserPage page,
			String keyword,
			ScraperExecutionContext context
	) {
		if (!openSearchPage(page, keyword)) {
			String message = "qafqazinfo.az search page did not load";
			log.warn(message);
			return SearchCollectionResult.failed(message);
		}
		SearchCardsResult cardsResult = collectSearchResultCards(page, keyword, context);
		if (cardsResult.cards().isEmpty() && cardsResult.stats().searchCardsFound() == 0) {
			return SearchCollectionResult.success(cardsResult.cards(), cardsResult.stats());
		}
		return SearchCollectionResult.success(cardsResult.cards(), cardsResult.stats());
	}

	private boolean openSearchPage(BrowserPage page, String keyword) {
		String fallbackUrl = searchUrl(keyword);
		try {
			log.info("Opening qafqazinfo.az search through UI flow: keyword={}", keyword);
			page.navigate(QafqazInfoAzScraperSupport.BASE_URL);
			waitForSearchInput(page);
			fillSearchInput(page, keyword);
			page.waitForTimeout(1_000);
			if (!isSearchUrl(page.url())) {
				log.warn("qafqazinfo.az UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			}
		} catch (BrowserEngineException exception) {
			log.warn("qafqazinfo.az UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
		}
		boolean loaded = waitForSearchPage(page);
		log.info("qafqazinfo.az search page opened: url={}, loaded={}", page.url(), loaded);
		return loaded;
	}

	private void waitForSearchInput(BrowserPage page) {
		try {
			page.waitForSelector(QafqazInfoAzSelectors.SEARCH_INPUT, 5_000);
		} catch (BrowserEngineException exception) {
			try {
				page.waitForSelector(QafqazInfoAzSelectors.SEARCH_INPUT_FALLBACK, 5_000);
			} catch (BrowserEngineException fallbackException) {
				page.waitForSelector(QafqazInfoAzSelectors.SEARCH_INPUT_PLACEHOLDER_FALLBACK, 5_000);
			}
		}
	}

	private void fillSearchInput(BrowserPage page, String keyword) {
		for (String selector : List.of(
				QafqazInfoAzSelectors.SEARCH_INPUT,
				QafqazInfoAzSelectors.SEARCH_INPUT_FALLBACK,
				QafqazInfoAzSelectors.SEARCH_INPUT_PLACEHOLDER_FALLBACK
		)) {
			try {
				page.fill(selector, "");
				page.fill(selector, keyword);
				page.press(selector, "Enter");
				return;
			} catch (BrowserEngineException exception) {
				log.debug("qafqazinfo.az search input selector failed: selector={}", selector);
			}
		}
		throw new BrowserEngineException("qafqazinfo.az search input was not found");
	}

	private boolean waitForSearchPage(BrowserPage page) {
		try {
			page.waitForSelector("body", 10_000);
			try {
				page.waitForSelector(QafqazInfoAzSelectors.SEARCH_RESULT_LINK, 5_000);
			} catch (BrowserTimeoutException exception) {
				try {
					page.waitForSelector(QafqazInfoAzSelectors.PAGINATION_ROOT, 3_000);
				} catch (BrowserTimeoutException paginationTimeout) {
					log.debug("qafqazinfo.az search cards/pagination did not appear before timeout: url={}", page.url());
				}
			}
			return isSearchUrl(page.url());
		} catch (BrowserTimeoutException exception) {
			return false;
		}
	}

	String searchUrl(String keyword) {
		return QafqazInfoAzScraperSupport.BASE_URL + "news/search?keyword="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8);
	}

	SearchCardsResult collectSearchResultCards(BrowserPage page, String keyword, ScraperExecutionContext context) {
		Map<String, QafqazInfoAzSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();
		Set<String> visitedPageUrls = new LinkedHashSet<>();
		Set<Integer> visitedPageNumbers = new LinkedHashSet<>();
		SearchCollectionStats stats = new SearchCollectionStats();
		int consecutivePagesWithoutNewPosts = 0;

		for (int pageIndex = 0; pageIndex < context.maxPages() && cardsByUrl.size() < context.maxPosts(); pageIndex++) {
			Document document = Jsoup.parse(page.content(), QafqazInfoAzScraperSupport.BASE_URL);
			String normalizedPageUrl = normalizePageUrl(page.url());
			int currentPage = currentPageNumber(page.url(), document);
			if (!visitedPageUrls.add(normalizedPageUrl) || !visitedPageNumbers.add(currentPage)) {
				log.warn("Stopping qafqazinfo.az pagination because page was already visited: page={}, url={}", currentPage, normalizedPageUrl);
				break;
			}
			stats.incrementPagesProcessed();
			PageCollectionStats pageStats = collectCurrentPageCards(document, currentPage, context, cardsByUrl, seenUrls, stats);
			log.info(
					"qafqazinfo.az search page={} stats: found={}, added={}, duplicates={}, invalidUrls={}",
					currentPage,
					pageStats.found(),
					pageStats.added(),
					pageStats.duplicates(),
					pageStats.invalidUrls()
			);

			if (cardsByUrl.size() >= context.maxPosts() || pageIndex + 1 >= context.maxPages()) {
				break;
			}
			if (pageStats.added() == 0) {
				consecutivePagesWithoutNewPosts++;
				if (consecutivePagesWithoutNewPosts >= MAX_CONSECUTIVE_PAGES_WITHOUT_NEW_POSTS) {
					log.info("Stopping qafqazinfo.az pagination after {} pages without new posts", consecutivePagesWithoutNewPosts);
					break;
				}
			} else {
				consecutivePagesWithoutNewPosts = 0;
			}
			Optional<String> nextPageUrl = nextPageUrl(document, page.url(), keyword, visitedPageUrls, visitedPageNumbers);
			if (nextPageUrl.isEmpty()) {
				break;
			}
			if (!openNextPage(page, nextPageUrl.get(), seenUrls, stats)) {
				break;
			}
		}
		if (!cardsByUrl.isEmpty()) {
			log.info("qafqazinfo.az candidate URLs: {}", cardsByUrl.keySet().stream().limit(10).toList());
		}
		return new SearchCardsResult(cardsByUrl.values().stream().limit(context.maxPosts()).toList(), stats);
	}

	private PageCollectionStats collectCurrentPageCards(
			Document document,
			int currentPage,
			ScraperExecutionContext context,
			Map<String, QafqazInfoAzSearchResultCard> cardsByUrl,
			Set<String> seenUrls,
			SearchCollectionStats totalStats
	) {
		Elements elements = document.select(QafqazInfoAzSelectors.SEARCH_RESULT_LINK_SCOPED);
		if (elements.isEmpty()) {
			elements = document.select(QafqazInfoAzSelectors.SEARCH_RESULT_LINK);
		}
		PageCollectionStats stats = new PageCollectionStats(elements.size());
		totalStats.addSearchCardsFound(elements.size());
		for (Element element : elements) {
			SearchCardParseResult parsedResult = parseResultCard(element, currentPage);
			if (parsedResult.card() == null) {
				stats = stats.incrementInvalidUrls();
				totalStats.incrementInvalidUrlsSkipped();
				continue;
			}
			QafqazInfoAzSearchResultCard card = parsedResult.card();
			if (!seenUrls.add(card.postUrl())) {
				stats = stats.incrementDuplicates();
				totalStats.incrementDuplicateUrlsSkipped();
				continue;
			}
			cardsByUrl.put(card.postUrl(), card);
			stats = stats.incrementAdded();
			if (cardsByUrl.size() >= context.maxPosts()) {
				break;
			}
		}
		return stats;
	}

	SearchCardParseResult parseResultCard(Element link, int searchPageNumber) {
		String normalizedUrl = QafqazInfoAzScraperSupport.normalizePostUrl(link.attr("href")).orElse(null);
		if (normalizedUrl == null) {
			return SearchCardParseResult.skipped();
		}
		String thumbnailUrl = Optional.ofNullable(link.selectFirst(QafqazInfoAzSelectors.SEARCH_RESULT_THUMBNAIL))
				.map(image -> image.attr("src"))
				.filter(QafqazInfoAzScraperSupport::isAllowedMediaUrl)
				.map(rawUrl -> UrlNormalizer.resolve(normalizedUrl, rawUrl))
				.filter(QafqazInfoAzScraperSupport::isAllowedMediaUrl)
				.orElse(null);
		return SearchCardParseResult.card(new QafqazInfoAzSearchResultCard(
				normalizedUrl,
				text(link, QafqazInfoAzSelectors.SEARCH_RESULT_TITLE),
				thumbnailUrl,
				searchPageNumber
		));
	}

	Optional<String> nextPageUrl(
			Document document,
			String currentUrl,
			String keyword,
			Set<String> visitedPageUrls,
			Set<Integer> visitedPageNumbers
	) {
		int currentPage = currentPageNumber(currentUrl, document);
		int nextPage = currentPage + 1;
		Optional<String> numberedLink = document.select(QafqazInfoAzSelectors.PAGINATION_PAGE_LINK)
				.stream()
				.filter(link -> parsePageNumber(link.text()).filter(pageNumber -> pageNumber == nextPage).isPresent())
				.map(link -> resolvePageHref(currentUrl, link.attr("href")))
				.filter(url -> !visitedPageUrls.contains(url))
				.findFirst();
		if (numberedLink.isPresent() && !visitedPageNumbers.contains(nextPage)) {
			return numberedLink;
		}
		Optional<String> nextLink = document.select(QafqazInfoAzSelectors.PAGINATION_NEXT_LINK)
				.stream()
				.map(link -> resolvePageHref(currentUrl, link.attr("href")))
				.filter(url -> !url.equals(normalizePageUrl(currentUrl)))
				.filter(url -> !visitedPageUrls.contains(url))
				.filter(url -> currentPageNumber(url, document) > currentPage)
				.findFirst();
		if (nextLink.isPresent()) {
			return nextLink;
		}
		return Optional.empty();
	}

	private String resolvePageHref(String currentUrl, String href) {
		if (href == null || href.isBlank()) {
			return normalizePageUrl(currentUrl);
		}
		String trimmed = href.trim();
		if (trimmed.startsWith("?")) {
			int queryIndex = currentUrl.indexOf('?');
			String base = queryIndex < 0 ? currentUrl : currentUrl.substring(0, queryIndex);
			return normalizePageUrl(base + trimmed);
		}
		return normalizePageUrl(UrlNormalizer.resolve(currentUrl, trimmed));
	}

	private boolean openNextPage(BrowserPage page, String nextPageUrl, Set<String> alreadySeenPostUrls, SearchCollectionStats stats) {
		Set<String> beforeUrls = collectPostUrls(Jsoup.parse(page.content(), QafqazInfoAzScraperSupport.BASE_URL));
		try {
			page.navigate(nextPageUrl);
			page.waitForTimeout(1_000);
			waitForSearchPage(page);
			Set<String> afterUrls = collectPostUrls(Jsoup.parse(page.content(), QafqazInfoAzScraperSupport.BASE_URL));
			boolean hasNewUrl = afterUrls.stream()
					.anyMatch(url -> !beforeUrls.contains(url) && !alreadySeenPostUrls.contains(url));
			if (!hasNewUrl) {
				log.warn("Stopping qafqazinfo.az pagination because next page has no new URLs: url={}", nextPageUrl);
			}
			return hasNewUrl;
		} catch (BrowserEngineException exception) {
			stats.incrementPaginationFailures();
			log.warn("Stopping qafqazinfo.az pagination because next page failed: url={}, error={}", nextPageUrl, exception.getMessage());
			return false;
		}
	}

	private Set<String> collectPostUrls(Document document) {
		Set<String> urls = new LinkedHashSet<>();
		document.select(QafqazInfoAzSelectors.SEARCH_RESULT_LINK)
				.stream()
				.map(link -> QafqazInfoAzScraperSupport.normalizePostUrl(link.attr("href")))
				.flatMap(Optional::stream)
				.forEach(urls::add);
		return urls;
	}

	private ArticleCollectionResult collectArticles(
			BrowserPage page,
			List<QafqazInfoAzSearchResultCard> cards,
			ScraperExecutionContext context
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenExternalIds = new LinkedHashSet<>();
		Set<String> seenPostUrls = new LinkedHashSet<>();
		ArticleCounters counters = new ArticleCounters();

		for (QafqazInfoAzSearchResultCard card : cards) {
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
					counters.mainImagesCollected += attempt.mainImagesCollected();
					if (attempt.dateSourceConflict()) {
						counters.dateSourceConflicts++;
					}
					posts.add(post);
				} else {
					counters.duplicates++;
					log.debug("Skipping duplicate qafqazinfo.az article: url={}, externalPostId={}", post.postUrl(), post.externalPostId());
				}
			} catch (BrowserTimeoutException exception) {
				counters.articleTimeouts++;
				log.warn("Skipping qafqazinfo.az article after timeout: url={}", card.postUrl());
			} catch (RuntimeException exception) {
				counters.articleErrors++;
				log.warn("Skipping qafqazinfo.az article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return counters.toResult(posts);
	}

	private ArticleCollectionAttempt collectArticleWithRetry(
			BrowserPage page,
			QafqazInfoAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		try {
			return collectArticle(page, card, context);
		} catch (BrowserTimeoutException exception) {
			log.warn("Timeout opening qafqazinfo.az article, retrying once: url={}", card.postUrl());
			return collectArticle(page, card, context);
		}
	}

	private ArticleCollectionAttempt collectArticle(
			BrowserPage page,
			QafqazInfoAzSearchResultCard card,
			ScraperExecutionContext context
	) {
		page.navigate(card.postUrl());
		page.waitForTimeout(500);
		Document document = Jsoup.parse(page.content(), card.postUrl());
		Element root = findArticleRoot(document).orElse(null);
		if (root == null) {
			log.warn("Skipping qafqazinfo.az article because article root was not found: url={}", card.postUrl());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.ARTICLE_ROOT_NOT_FOUND);
		}
		QafqazInfoAzDateParser.DateParseResult parsedDate = parseArticleDate(root, card).orElse(null);
		if (parsedDate == null) {
			log.warn("Skipping qafqazinfo.az article with missing or invalid date: url={}", card.postUrl());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.INVALID_DATE);
		}
		OffsetDateTime postDate = parsedDate.date();
		if (DateRangeValidator.isAfterRange(postDate, context.dateTo())) {
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.TOO_NEW);
		}
		if (DateRangeValidator.isBeforeRange(postDate, context.dateFrom())) {
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.TOO_OLD);
		}

		String text = extractArticleText(root);
		if (text == null || text.isBlank()) {
			log.warn("Skipping qafqazinfo.az article with empty text: url={}, title={}", card.postUrl(), card.title());
			return ArticleCollectionAttempt.skipped(ArticleSkipReason.EMPTY_TEXT);
		}

		String externalPostId = QafqazInfoAzScraperSupport.extractExternalPostId(card.postUrl())
				.orElseGet(() -> Integer.toHexString(card.postUrl().hashCode()));
		String title = firstNonBlank(text(root, QafqazInfoAzSelectors.ARTICLE_TITLE), card.title());
		MediaExtractionResult mediaResult = extractMedia(root, card);
		Map<String, Object> metadata = metadata(context, card, title);

		log.info("Collected qafqazinfo.az article: url={}, mediaCount={}", card.postUrl(), mediaResult.media().size());
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
				mediaResult.mainImagesCollected(),
				parsedDate.conflict()
		);
	}

	Optional<Element> findArticleRoot(Document document) {
		return document.select(QafqazInfoAzSelectors.ARTICLE_ROOT)
				.stream()
				.filter(element -> element.selectFirst(QafqazInfoAzSelectors.ARTICLE_TITLE) != null)
				.filter(element -> element.selectFirst(QafqazInfoAzSelectors.ARTICLE_TIME) != null)
				.filter(element -> element.selectFirst(QafqazInfoAzSelectors.ARTICLE_TEXT) != null)
				.findFirst();
	}

	Optional<QafqazInfoAzDateParser.DateParseResult> parseArticleDate(Element root, QafqazInfoAzSearchResultCard card) {
		Element time = root.selectFirst(QafqazInfoAzSelectors.ARTICLE_TIME);
		if (time == null) {
			return Optional.empty();
		}
		return dateParser.parseArticleDate(time.text(), time.attr("datetime"), card.postUrl());
	}

	String extractArticleText(Element root) {
		Element body = root.selectFirst(QafqazInfoAzSelectors.ARTICLE_TEXT);
		if (body == null) {
			return null;
		}
		Element cleanBody = body.clone();
		cleanBody.select(ARTICLE_CLEANUP_SELECTOR).remove();
		String text = cleanBody.select("p")
				.stream()
				.map(Element::text)
				.map(this::normalizeArticleText)
				.filter(this::isArticleParagraph)
				.reduce((left, right) -> left + "\n" + right)
				.orElse(null);
		return isUsableArticleText(text) ? text : null;
	}

	private boolean isArticleParagraph(String text) {
		if (text == null || text.length() < MIN_PARAGRAPH_TEXT_LENGTH) {
			return false;
		}
		String normalized = text.toLowerCase(Locale.ROOT);
		return !normalized.contains("oxunma sayı")
				&& !normalized.contains("facebook")
				&& !normalized.contains("telegram")
				&& !normalized.contains("whatsapp")
				&& !normalized.contains("dynamic_banners")
				&& !normalized.contains("banner");
	}

	private boolean isUsableArticleText(String text) {
		return text != null && text.length() >= MIN_ARTICLE_TEXT_LENGTH;
	}

	MediaExtractionResult extractMedia(Element root, QafqazInfoAzSearchResultCard card) {
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		int mainImagesCollected = 0;
		Element mainImage = root.children()
				.stream()
				.filter(element -> element.is(QafqazInfoAzSelectors.ARTICLE_MAIN_IMAGE))
				.findFirst()
				.orElseGet(() -> root.select(QafqazInfoAzSelectors.ARTICLE_MAIN_IMAGE)
						.stream()
						.filter(image -> image.parents().stream().noneMatch(parent -> parent.hasClass("news_text")
								|| parent.hasClass("social-buttons")
								|| parent.className().toLowerCase(Locale.ROOT).contains("banner")))
						.findFirst()
						.orElse(null));
		if (mainImage != null) {
			String rawUrl = mainImage.attr("src");
			if (QafqazInfoAzScraperSupport.isAllowedMediaUrl(rawUrl)) {
				String normalizedUrl = UrlNormalizer.resolve(card.postUrl(), rawUrl);
				if (QafqazInfoAzScraperSupport.isAllowedMediaUrl(normalizedUrl)) {
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

	private Map<String, Object> metadata(ScraperExecutionContext context, QafqazInfoAzSearchResultCard card, String title) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", QafqazInfoAzScraperSupport.SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		if (title != null && !title.isBlank()) {
			metadata.put("title", title);
		}
		metadata.put("searchPageNumber", card.searchPageNumber());
		return metadata;
	}

	int currentPageNumber(String currentUrl, Document document) {
		return pageNumberFromUrl(currentUrl)
				.or(() -> document.select(QafqazInfoAzSelectors.PAGINATION_PAGE_LINK)
						.stream()
						.map(link -> parsePageNumber(link.text()))
						.flatMap(Optional::stream)
						.min(Comparator.naturalOrder())
						.map(number -> Math.max(1, number - 1)))
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
		return url != null && url.toLowerCase(Locale.ROOT).contains("/news/search");
	}

	private String normalizePageUrl(String url) {
		return UrlNormalizer.removeTrackingParams(url);
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

	record SearchCardParseResult(QafqazInfoAzSearchResultCard card) {

		static SearchCardParseResult card(QafqazInfoAzSearchResultCard card) {
			return new SearchCardParseResult(card);
		}

		static SearchCardParseResult skipped() {
			return new SearchCardParseResult(null);
		}
	}

	record SearchCollectionResult(
			List<QafqazInfoAzSearchResultCard> cards,
			SearchCollectionStats stats,
			String failureMessage
	) {

		static SearchCollectionResult success(List<QafqazInfoAzSearchResultCard> cards, SearchCollectionStats stats) {
			return new SearchCollectionResult(cards, stats, null);
		}

		static SearchCollectionResult failed(String message) {
			return new SearchCollectionResult(List.of(), new SearchCollectionStats(), message);
		}
	}

	record SearchCardsResult(List<QafqazInfoAzSearchResultCard> cards, SearchCollectionStats stats) {
	}

	record PageCollectionStats(int found, int added, int duplicates, int invalidUrls) {

		PageCollectionStats(int found) {
			this(found, 0, 0, 0);
		}

		PageCollectionStats incrementAdded() {
			return new PageCollectionStats(found, added + 1, duplicates, invalidUrls);
		}

		PageCollectionStats incrementDuplicates() {
			return new PageCollectionStats(found, added, duplicates + 1, invalidUrls);
		}

		PageCollectionStats incrementInvalidUrls() {
			return new PageCollectionStats(found, added, duplicates, invalidUrls + 1);
		}
	}

	static final class SearchCollectionStats {

		private int searchCardsFound;
		private int duplicateUrlsSkipped;
		private int invalidUrlsSkipped;
		private int pagesProcessed;
		private int paginationFailures;

		int searchCardsFound() {
			return searchCardsFound;
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

		void addSearchCardsFound(int count) {
			searchCardsFound += count;
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
	}

	record ArticleCollectionAttempt(
			ScrapedPostDTO post,
			ArticleSkipReason skipReason,
			int mainImagesCollected,
			boolean dateSourceConflict
	) {

		static ArticleCollectionAttempt collected(ScrapedPostDTO post, int mainImagesCollected, boolean dateSourceConflict) {
			return new ArticleCollectionAttempt(post, null, mainImagesCollected, dateSourceConflict);
		}

		static ArticleCollectionAttempt skipped(ArticleSkipReason reason) {
			return new ArticleCollectionAttempt(null, reason, 0, false);
		}
	}

	record ArticleCollectionResult(
			List<ScrapedPostDTO> posts,
			int articlePagesOpened,
			int articleTimeouts,
			int articleRootNotFound,
			int dateParseFailures,
			int dateSourceConflicts,
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
			String message = ("qafqazinfo.az extraction failed: candidatesFound=%d, articlePagesOpened=%d, "
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
		private int dateSourceConflicts;
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
					dateSourceConflicts,
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
