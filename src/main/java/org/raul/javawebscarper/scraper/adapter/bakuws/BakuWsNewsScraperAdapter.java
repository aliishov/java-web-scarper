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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class BakuWsNewsScraperAdapter implements NewsScraperAdapter {

	private static final String SEARCH_INPUT_SELECTOR = "form.custom-navbar-search-block input[name='query']";
	private static final String SEARCH_ICON_SELECTOR = "svg.svg-icon.normal";
	private static final String RESULT_CARD_SELECTOR = ".post-item";
	private static final String[] ARTICLE_TEXT_SELECTORS = {
			".news-inner",
			".post-content",
			".post-text",
			".news-content",
			"article",
			"main"
	};
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
			List<ScrapedPostDTO> posts = collectArticles(page, cards, context);
			log.info("Finished baku.ws scraping: keyword={}, postsFound={}", keyword, posts.size());
			return posts.isEmpty() ? ScraperExecutionResult.empty() : ScraperExecutionResult.success(posts);
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
			page.click(SEARCH_ICON_SELECTOR);
			page.fill(SEARCH_INPUT_SELECTOR, keyword);
			page.press(SEARCH_INPUT_SELECTOR, "Enter");
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

	private String searchUrl(String keyword) {
		return BakuWsScraperSupport.BASE_URL + "search?query="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8);
	}

	private List<BakuWsSearchResultCard> collectSearchResultCards(BrowserPage page, ScraperExecutionContext context) {
		Map<String, BakuWsSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();

		for (int scroll = 0; scroll < context.maxPages() && cardsByUrl.size() < context.maxPosts(); scroll++) {
			CardCollectionStats stats = collectCurrentCards(page, context, cardsByUrl, seenUrls);
			log.info(
					"baku.ws search cards pass={}: found={}, added={}, duplicates={}, outOfRange={}, old={}",
					scroll + 1,
					stats.found(),
					stats.added(),
					stats.duplicates(),
					stats.afterRange(),
					stats.beforeRange()
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
		return cardsByUrl.values().stream().limit(context.maxPosts()).toList();
	}

	private CardCollectionStats collectCurrentCards(
			BrowserPage page,
			ScraperExecutionContext context,
			Map<String, BakuWsSearchResultCard> cardsByUrl,
			Set<String> seenUrls
	) {
		Document document = Jsoup.parse(page.content(), BakuWsScraperSupport.BASE_URL);
		Elements elements = document.select(RESULT_CARD_SELECTOR);
		int found = elements.size();
		int added = 0;
		int duplicates = 0;
		int beforeRange = 0;
		int afterRange = 0;
		int malformed = 0;
		int newDatedCards = 0;

		for (Element element : elements) {
			Optional<BakuWsSearchResultCard> parsedCard = parseResultCard(element);
			if (parsedCard.isEmpty()) {
				malformed++;
				continue;
			}
			BakuWsSearchResultCard card = parsedCard.get();
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
		return new CardCollectionStats(found, added, duplicates, beforeRange, afterRange, malformed, newDatedCards);
	}

	private Optional<BakuWsSearchResultCard> parseResultCard(Element element) {
		if (element.parents().stream().anyMatch(parent -> parent.hasClass("cat-left-bnr"))) {
			return Optional.empty();
		}
		String postUrl = firstNonBlank(
				element.selectFirst(".post-item-title a") == null ? null : element.selectFirst(".post-item-title a").attr("href"),
				element.selectFirst(".post-item-img a") == null ? null : element.selectFirst(".post-item-img a").attr("href"),
				element.selectFirst(".post-item-content[data-url]") == null
						? null
						: element.selectFirst(".post-item-content[data-url]").attr("data-url")
		);
		if (postUrl == null) {
			log.warn("Skipping baku.ws card without post URL");
			return Optional.empty();
		}
		String normalizedUrl = BakuWsScraperSupport.normalizePostUrl(postUrl);
		String time = text(element, ".post-item-date-time");
		String day = text(element, ".post-item-date-day");
		Optional<OffsetDateTime> postDate = dateParser.parseResultCardDate(day, time);
		if (postDate.isEmpty()) {
			log.warn("Skipping baku.ws card with unparseable date: url={}, day={}, time={}", normalizedUrl, day, time);
			return Optional.empty();
		}
		String thumbnailUrl = Optional.ofNullable(element.selectFirst(".post-item-img img"))
				.map(image -> firstNonBlank(image.attr("src"), image.attr("data-src")))
				.filter(value -> !value.isBlank())
				.map(value -> UrlNormalizer.resolve(BakuWsScraperSupport.BASE_URL, value))
				.filter(BakuWsScraperSupport::isAllowedMediaUrl)
				.orElse(null);

		return Optional.of(new BakuWsSearchResultCard(
				normalizedUrl,
				text(element, ".post-item-title a"),
				postDate.get(),
				thumbnailUrl
		));
	}

	private List<ScrapedPostDTO> collectArticles(
			BrowserPage page,
			List<BakuWsSearchResultCard> cards,
			ScraperExecutionContext context
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenExternalIds = new LinkedHashSet<>();

		for (BakuWsSearchResultCard card : cards) {
			if (posts.size() >= context.maxPosts()) {
				break;
			}
			try {
				collectArticleWithRetry(page, card, context).ifPresent(post -> {
					if (seenExternalIds.add(post.externalPostId())) {
						posts.add(post);
					} else {
						log.debug("Skipping duplicate baku.ws article by externalPostId={}", post.externalPostId());
					}
				});
			} catch (RuntimeException exception) {
				log.warn("Skipping baku.ws article after parse failure: url={}, error={}", card.postUrl(), exception.getMessage());
			}
		}
		return posts;
	}

	private Optional<ScrapedPostDTO> collectArticleWithRetry(
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

	private Optional<ScrapedPostDTO> collectArticle(
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
			return Optional.empty();
		}

		String text = extractArticleText(document);
		if (text == null || text.isBlank()) {
			log.warn("Skipping baku.ws article with empty text: url={}", card.postUrl());
			return Optional.empty();
		}

		String externalPostId = BakuWsScraperSupport.extractExternalPostId(card.postUrl())
				.orElseGet(() -> Integer.toHexString(card.postUrl().hashCode()));
		List<ScrapedMediaDTO> media = extractMedia(document, card);
		Map<String, Object> metadata = metadata(context, card);

		log.info("Collected baku.ws article: url={}, mediaCount={}", card.postUrl(), media.size());
		return Optional.of(new ScrapedPostDTO(
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

	private Optional<OffsetDateTime> parseArticleDate(Document document) {
		String day = text(document, ".post-date-day");
		String month = text(document, ".post-date-month");
		String year = text(document, ".post-date-year");
		String time = text(document, ".post-date-time");
		if (day == null || month == null || year == null || time == null) {
			return Optional.empty();
		}
		return dateParser.parseArticleDate(day, month, year, time);
	}

	private String extractArticleText(Document document) {
		String bestText = null;
		for (String selector : ARTICLE_TEXT_SELECTORS) {
			Element element = document.selectFirst(selector);
			if (element == null) {
				continue;
			}
			Element cleanElement = element.clone();
			cleanElement.select("script, style, nav, footer, aside, header, .cat-left-bnr, [class*=banner], .post-date")
					.remove();
			String text = cleanElement.text().trim();
			if (text.length() > (bestText == null ? 0 : bestText.length())) {
				bestText = text;
			}
		}
		return bestText;
	}

	private List<ScrapedMediaDTO> extractMedia(Document document, BakuWsSearchResultCard card) {
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		for (String selector : ARTICLE_TEXT_SELECTORS) {
			Element root = document.selectFirst(selector);
			if (root == null) {
				continue;
			}
			collectMedia(root, card.postUrl(), mediaByUrl);
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

	private void collectMedia(Element root, String baseUrl, Map<String, MediaType> mediaByUrl) {
		for (Element element : root.select("img[src], img[data-src], video[src], video source[src], source[src]")) {
			String rawUrl = firstNonBlank(element.attr("src"), element.attr("data-src"));
			if (rawUrl == null) {
				continue;
			}
			String normalizedUrl = UrlNormalizer.resolve(baseUrl, rawUrl);
			if (BakuWsScraperSupport.isAllowedMediaUrl(normalizedUrl)) {
				mediaByUrl.putIfAbsent(normalizedUrl, BakuWsScraperSupport.mediaTypeForTag(element.tagName()));
			}
		}
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

	private record CardCollectionStats(
			int found,
			int added,
			int duplicates,
			int beforeRange,
			int afterRange,
			int malformed,
			int newDatedCards
	) {

		private boolean onlyNewCardsAreOld() {
			return newDatedCards > 0 && beforeRange == newDatedCards;
		}
	}
}
