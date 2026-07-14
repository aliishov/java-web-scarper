package org.raul.javawebscarper.scraper.adapter.lentaz;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LentAzNewsScraperAdapterTests {

	private final LentAzNewsScraperAdapter adapter = new LentAzNewsScraperAdapter(
			null,
			new LentAzDateParser(ZoneId.of("Asia/Baku")),
			new LentAzSearchPeriodResolver("Asia/Baku")
	);

	@Test
	void supportsLentAzSource() {
		Source source = Source.builder()
				.code("LENT_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("LENT_AZ");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void supportsLentAzBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://lent.az/")
				.build();

		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void buildsEncodedFallbackSearchUrlWithSelectedType() {
		assertThat(adapter.searchUrl("court news", 2))
				.isEqualTo("https://lent.az/axtaris-neticesi?search=court+news&type=2");
	}

	@Test
	void extractsSearchCardTitleUrlDateThumbnailAndExternalId() {
		Element card = Jsoup.parse("""
				<div class="item" id="news_470101" data-id="470101">
				  <div class="item_head">
				    <a class="overlay" href="http://lent.az/xeber/sosial/example-title-470101">
				      <span>11:40</span>
				      <span>27 iyun 2026</span>
				    </a>
				    <img src="/uploads/news/2026/06/main.jpg">
				  </div>
				  <div class="item_foot">
				    <a class="title" href="/xeber/sosial/example-title-470101">
				      <h3>Example <span class="red_color">title</span></h3>
				    </a>
				  </div>
				</div>
				""", "https://lent.az/")
				.selectFirst(".item");

		LentAzNewsScraperAdapter.SearchCardParseResult parsed = adapter.parseResultCard(card, 3);

		assertThat(parsed.card()).isNotNull();
		assertThat(parsed.card().postUrl()).isEqualTo("https://lent.az/xeber/sosial/example-title-470101");
		assertThat(parsed.card().externalPostId()).isEqualTo("470101");
		assertThat(parsed.card().title()).isEqualTo("Example title");
		assertThat(parsed.card().searchDate()).isEqualTo(OffsetDateTime.parse("2026-06-27T11:40:00+04:00"));
		assertThat(parsed.card().thumbnailUrl()).isEqualTo("https://lent.az/uploads/news/2026/06/main.jpg");
		assertThat(parsed.card().searchPageNumber()).isEqualTo(3);
	}

	@Test
	void skipsAdCardsAndInvalidUrls() {
		Document document = Jsoup.parse("""
				<div class="item rek_item" id="news_1" data-id="1">
				  <a class="overlay" href="/xeber/sosial/ad-1">Ad</a>
				</div>
				<div class="item" id="news_2" data-id="2">
				  <a class="overlay" href="/layihe/example">Project</a>
				</div>
				<div class="item custom-banner">
				  <a class="overlay" href="/xeber/sosial/banner-3">Banner</a>
				</div>
				""", "https://lent.az/");

		assertThat(adapter.parseResultCard(document.select(".item").get(0), 1).card()).isNull();
		assertThat(adapter.parseResultCard(document.select(".item").get(1), 1).card()).isNull();
		assertThat(adapter.parseResultCard(document.select(".item").get(2), 1).card()).isNull();
	}

	@Test
	void resolvesRelNextAndNormalizesHttpToHttps() {
		Document document = Jsoup.parse("""
				<ul class="pagination">
				  <li class="active_li">1</li>
				  <li><a rel="next" href="http://lent.az/axtaris-neticesi?search=court&type=1&page=2">Next</a></li>
				</ul>
				""", "https://lent.az/axtaris-neticesi?search=court&type=1");

		assertThat(adapter.nextPageUrl(
				document,
				"https://lent.az/axtaris-neticesi?search=court&type=1",
				"court",
				1,
				new LinkedHashSet<>(),
				Set.of(1)
		)).contains("https://lent.az/axtaris-neticesi?search=court&type=1&page=2");
	}

	@Test
	void doesNotLoopToVisitedPaginationPage() {
		Document document = Jsoup.parse("""
				<ul class="pagination">
				  <li class="active_li">1</li>
				  <li><a rel="next" href="https://lent.az/axtaris-neticesi?search=court&type=1&page=1">Next</a></li>
				</ul>
				""", "https://lent.az/axtaris-neticesi?search=court&type=1&page=1");

		assertThat(adapter.nextPageUrl(
				document,
				"https://lent.az/axtaris-neticesi?search=court&type=1&page=1",
				"court",
				1,
				new LinkedHashSet<>(),
				Set.of(1, 2)
		)).isEmpty();
	}

	@Test
	void fallsBackToGeneratedPaginationUrlWhenRootExists() {
		Document document = Jsoup.parse("""
				<ul class="pagination">
				  <li class="active_li">1</li>
				</ul>
				""", "https://lent.az/axtaris-neticesi?search=court&type=1");

		assertThat(adapter.nextPageUrl(
				document,
				"https://lent.az/axtaris-neticesi?search=court&type=1",
				"court",
				1,
				new LinkedHashSet<>(),
				Set.of(1)
		)).contains("https://lent.az/axtaris-neticesi?search=court&type=1&page=2");
	}

	@Test
	void parsesArticleDateBeforeSearchCardFallback() {
		Element root = articleRoot("""
				<div class="news_img">
				  <div class="overlay"><span>27 iyun 2026 11:40 (UTC +04:00)</span></div>
				</div>
				<div class="news_content">
				  <p>This article paragraph is intentionally long enough to make the fixture valid.</p>
				</div>
				""");

		assertThat(adapter.parseArticleDate(root, articleCard()))
				.contains(OffsetDateTime.parse("2026-06-27T11:40:00+04:00"));
	}

	@Test
	void extractsArticleTextOnlyFromScopedParagraphs() {
		Element root = articleRoot("""
				<h1 class="news_title">Title must not be part of article text</h1>
				<div class="category">Category text must not be collected</div>
				<div class="news_content">
				  <p>The first article paragraph is long enough to be accepted by the extractor.</p>
				  <p>The second article paragraph should stay in the normalized text result.</p>
				  <div class="reaction-wrap">
				    <p>Reaction widget paragraph should be ignored even when it is long.</p>
				  </div>
				  <div class="emoji-container">
				    <p>Emoji paragraph should be ignored even when it is long enough.</p>
				  </div>
				</div>
				""");

		String text = adapter.extractArticleText(root);

		assertThat(text)
				.contains("The first article paragraph is long enough")
				.contains("The second article paragraph should stay")
				.doesNotContain("Title must not be part")
				.doesNotContain("Category text")
				.doesNotContain("Reaction widget")
				.doesNotContain("Emoji paragraph");
	}

	@Test
	void extractsOnlyMainArticleImageAndIgnoresBodyAds() {
		Element root = articleRoot("""
				<div class="news_img">
				  <img src="/uploads/news/2026/06/main.jpg">
				</div>
				<div class="news_content">
				  <p>The article paragraph is intentionally long enough for the media test.</p>
				  <img src="/uploads/news/2026/06/body.jpg">
				  <img src="https://newmedia.az/ad/banner.jpg">
				</div>
				""");

		LentAzNewsScraperAdapter.MediaExtractionResult result = adapter.extractMedia(root, articleCard());

		assertThat(result.mainImagesCollected()).isEqualTo(1);
		assertThat(result.media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://lent.az/uploads/news/2026/06/main.jpg");
		assertThat(result.media())
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE);
	}

	@Test
	void fallsBackToSearchThumbnailWhenArticleImageMissing() {
		Element root = articleRoot("""
				<div class="news_content">
				  <p>The article paragraph is intentionally long enough for thumbnail fallback media test.</p>
				</div>
				""");

		LentAzNewsScraperAdapter.MediaExtractionResult result = adapter.extractMedia(root, articleCard());

		assertThat(result.mainImagesCollected()).isZero();
		assertThat(result.media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://lent.az/uploads/thumb.jpg");
	}

	private Element articleRoot(String body) {
		return Jsoup.parse("""
				<html>
				  <body>
				    <div id="content" class="left_column">
				      %s
				    </div>
				  </body>
				</html>
				""".formatted(body), "https://lent.az/xeber/sosial/example-title-470101")
				.selectFirst("#content.left_column");
	}

	private LentAzSearchResultCard articleCard() {
		return new LentAzSearchResultCard(
				"https://lent.az/xeber/sosial/example-title-470101",
				"470101",
				"Example title",
				OffsetDateTime.parse("2026-06-26T10:30:00+04:00"),
				"https://lent.az/uploads/thumb.jpg",
				2
		);
	}
}
