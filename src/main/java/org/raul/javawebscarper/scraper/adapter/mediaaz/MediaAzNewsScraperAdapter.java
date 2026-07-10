package org.raul.javawebscarper.scraper.adapter.mediaaz;

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
import java.time.LocalDate;
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
public class MediaAzNewsScraperAdapter implements NewsScraperAdapter {

	private static final String SEARCH_TOGGLE_SELECTOR = "button.header__tool.header__search-open";
	private static final String SEARCH_INPUT_SELECTOR = "form.header__search input[name='query']";
	private static final String SEARCH_SUBMIT_SELECTOR = "form.header__search button[type='submit']";
	private static final String RESULT_CARD_SELECTOR = "div.post-block[data-timestamp]";

	private final BrowserSessionFactory browserSessionFactory;
	private final MediaAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return MediaAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return MediaAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < 3) {
			return ScraperExecutionResult.failed("media.az search keyword must contain at least 3 characters");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		log.info(
				"Starting media.az scraping: keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
				keyword,
				context.dateFrom(),
				context.dateTo(),
				context.maxPages(),
				context.maxPosts()
		);

		try (BrowserSession session = browserSessionFactory.createSession()) {
			BrowserPage page = session.newPage();
			openSearchPage(page, keyword.trim(), context);
			List<MediaAzSearchResultCard> cards = collectSearchResultCards(page, context);
			if (cards.isEmpty()) {
				String fallbackUrl = datedSearchUrl(
						keyword.trim(),
						context.dateFrom().toLocalDate(),
						context.dateTo().toLocalDate(),
						false
				);
				log.info("media.az dated search returned no cards, retrying with empty date_end: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
				cards = collectSearchResultCards(page, context);
			}
			log.info("Finished media.az search collection: keyword={}, candidatesFound={}", keyword, cards.size());
			return ScraperExecutionResult.empty();
		} catch (BrowserEngineException exception) {
			log.warn("media.az scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("media.az scraping failed: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected media.az scraping failure", exception);
			return ScraperExecutionResult.failed("Unexpected media.az scraping failure: " + exception.getMessage());
		}
	}

	private void openSearchPage(BrowserPage page, String keyword, ScraperExecutionContext context) {
		String fallbackUrl = datedSearchUrl(
				keyword,
				context.dateFrom().toLocalDate(),
				context.dateTo().toLocalDate(),
				true
		);
		try {
			log.info("Opening media.az search through UI flow: keyword={}", keyword);
			page.navigate(MediaAzScraperSupport.BASE_URL);
			page.click(SEARCH_TOGGLE_SELECTOR);
			page.fill(SEARCH_INPUT_SELECTOR, keyword);
			page.press(SEARCH_INPUT_SELECTOR, "Enter");
			page.waitForTimeout(1_000);
			if (!page.url().contains("/search") || !page.url().contains("query")) {
				log.warn("media.az UI search did not navigate to search page, using fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			} else {
				log.info("media.az UI search opened, applying dated fallback URL: {}", fallbackUrl);
				page.navigate(fallbackUrl);
				page.waitForTimeout(1_000);
			}
		} catch (BrowserEngineException exception) {
			log.warn("media.az UI search flow failed, using fallback URL: {}", fallbackUrl);
			page.navigate(fallbackUrl);
			page.waitForTimeout(1_000);
		}
		log.info("media.az search page opened: {}", page.url());
	}

	String searchUrl(String keyword) {
		return MediaAzScraperSupport.BASE_URL + "search?query="
				+ URLEncoder.encode(keyword, StandardCharsets.UTF_8);
	}

	String datedSearchUrl(String keyword, LocalDate dateFrom, LocalDate dateTo, boolean includeDateEnd) {
		String dateEnd = includeDateEnd && dateTo != null ? dateTo.toString() : "";
		return searchUrl(keyword)
				+ "&date_start=" + (dateFrom == null ? "" : dateFrom)
				+ "&date_end=" + dateEnd
				+ "&category=&sort_type=0";
	}

	private List<MediaAzSearchResultCard> collectSearchResultCards(
			BrowserPage page,
			ScraperExecutionContext context
	) {
		Map<String, MediaAzSearchResultCard> cardsByUrl = new LinkedHashMap<>();
		Set<String> seenUrls = new LinkedHashSet<>();

		for (int scroll = 0; scroll < context.maxPages() && cardsByUrl.size() < context.maxPosts(); scroll++) {
			CardCollectionStats stats = collectCurrentCards(page, context, cardsByUrl, seenUrls);
			log.info(
					"media.az search cards pass={}: found={}, added={}, duplicateCards={}, "
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
			log.info("media.az candidate URLs: {}", cardsByUrl.keySet().stream().limit(10).toList());
		}
		return cardsByUrl.values().stream().limit(context.maxPosts()).toList();
	}

	private CardCollectionStats collectCurrentCards(
			BrowserPage page,
			ScraperExecutionContext context,
			Map<String, MediaAzSearchResultCard> cardsByUrl,
			Set<String> seenUrls
	) {
		Document document = Jsoup.parse(page.content(), MediaAzScraperSupport.BASE_URL);
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
			Optional<MediaAzSearchResultCard> parsedCard = parseResultCard(element);
			if (parsedCard.isEmpty()) {
				if (hasResultUrl(element)) {
					skippedDateParse++;
				} else {
					skippedMissingUrl++;
				}
				continue;
			}
			MediaAzSearchResultCard card = parsedCard.get();
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

	Optional<MediaAzSearchResultCard> parseResultCard(Element element) {
		String postUrl = attr(element, "a.news__item[href]", "href");
		if (postUrl == null) {
			return Optional.empty();
		}
		String normalizedUrl = MediaAzScraperSupport.normalizePostUrl(postUrl);
		Optional<OffsetDateTime> postDate = dateParser.parseResultCardTimestamp(element.attr("data-timestamp"));
		if (postDate.isEmpty()) {
			postDate = dateParser.parseVisibleDate(text(element, ".news__date"));
		}
		if (postDate.isEmpty()) {
			log.warn(
					"Skipping media.az card with unparseable date: url={}, dataTimestamp={}, visibleDate={}",
					normalizedUrl,
					element.attr("data-timestamp"),
					text(element, ".news__date")
			);
			return Optional.empty();
		}
		String thumbnailUrl = Optional.ofNullable(element.selectFirst(".news__image img"))
				.map(image -> firstNonBlank(image.attr("src"), image.attr("data-src")))
				.filter(value -> !value.isBlank())
				.map(value -> UrlNormalizer.resolve(MediaAzScraperSupport.BASE_URL, value))
				.filter(MediaAzScraperSupport::isAllowedMediaUrl)
				.orElse(null);

		return Optional.of(new MediaAzSearchResultCard(
				normalizedUrl,
				firstNonBlank(
						text(element, ".news__title"),
						text(element, ".news__item-title"),
						text(element, ".news__item h3"),
						text(element, ".news__item h2")
				),
				postDate.get(),
				thumbnailUrl
		));
	}

	private boolean hasResultUrl(Element element) {
		return attr(element, "a.news__item[href]", "href") != null;
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
