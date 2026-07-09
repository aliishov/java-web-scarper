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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
			log.info("Finished oxu.az search collection: keyword={}, candidatesFound={}", keyword, cards.size());
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
