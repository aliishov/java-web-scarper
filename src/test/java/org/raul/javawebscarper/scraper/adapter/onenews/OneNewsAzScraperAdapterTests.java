package org.raul.javawebscarper.scraper.adapter.onenews;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OneNewsAzScraperAdapterTests {

	private final OneNewsAzScraperAdapter adapter = new OneNewsAzScraperAdapter(
			null,
			new OneNewsAzDateParser(ZoneId.of("Asia/Baku"))
	);

	@Test
	void supportsOneNewsAzSourceCode() {
		Source source = Source.builder()
				.code("ONE_NEWS_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("ONE_NEWS_AZ");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void supportsOneNewsBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://1news.az/az")
				.build();

		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void buildsEncodedFallbackSearchUrl() {
		assertThat(adapter.searchUrl("ilham eliyev"))
				.isEqualTo("https://1news.az/az/axtarish/?q=ilham+eliyev");
	}

	@Test
	void extractsArticleSearchResult() {
		Element result = Jsoup.parse("""
				<div class="gsc-webResult gsc-result">
				  <div class="gs-title">
				    <a href="https://1news.az/az/news/20260617191207105-Yasamaldaki-yanginla-bagli-cinayet-ishi-mehkeme-baxishina-verildi">
				      Yasamaldaki yanginla bagli cinayet ishi - 1news.az
				    </a>
				  </div>
				  <div class="gs-snippet">19:12 - 17 / 06 / 2026 - Article snippet</div>
				</div>
				""", "https://1news.az/az")
				.selectFirst(".gsc-result");

		OneNewsAzScraperAdapter.SearchCardParseResult parsed = adapter.parseResultCard(result, 2);

		assertThat(parsed.card()).isNotNull();
		assertThat(parsed.card().postUrl())
				.isEqualTo("https://1news.az/az/news/20260617191207105-Yasamaldaki-yanginla-bagli-cinayet-ishi-mehkeme-baxishina-verildi");
		assertThat(parsed.card().title()).isEqualTo("Yasamaldaki yanginla bagli cinayet ishi");
		assertThat(parsed.card().searchDate()).isEqualTo(OffsetDateTime.parse("2026-06-17T19:12:00+04:00"));
		assertThat(parsed.card().searchPageNumber()).isEqualTo(2);
	}

	@Test
	void rejectsExternalSearchResultUrl() {
		Element result = searchResult("https://example.com/az/news/20260617191207105-title");

		OneNewsAzScraperAdapter.SearchCardParseResult parsed = adapter.parseResultCard(result, 1);

		assertThat(parsed.card()).isNull();
		assertThat(parsed.skipReason())
				.isEqualTo(OneNewsAzScraperAdapter.SearchCardSkipReason.EXTERNAL_OR_INVALID_URL);
	}

	@Test
	void rejectsSearchAuthorAndCategoryUrls() {
		assertThat(adapter.parseResultCard(searchResult("https://1news.az/az/axtarish/?q=test"), 1).card()).isNull();
		assertThat(adapter.parseResultCard(searchResult("https://1news.az/az/muellif/27-Oksana-Orucova"), 1).card()).isNull();
		assertThat(adapter.parseResultCard(searchResult("https://1news.az/az/siyaset"), 1).card()).isNull();
	}

	@Test
	void extractsExternalIdFromArticleUrl() {
		assertThat(OneNewsAzScraperSupport.extractExternalPostId(
				"https://1news.az/az/news/20260617191207105-Yasamaldaki-yanginla-bagli-cinayet-ishi-mehkeme-baxishina-verildi"
		)).contains("20260617191207105");
	}

	@Test
	void findsNextPaginationPageWithoutReturningCurrentPage() {
		Document document = Jsoup.parse("""
				<div class="gsc-cursor">
				  <div class="gsc-cursor-page">1</div>
				  <div class="gsc-cursor-page gsc-cursor-current-page">2</div>
				  <div class="gsc-cursor-page">3</div>
				</div>
				""");

		assertThat(adapter.currentPageNumber(document)).isEqualTo(2);
		assertThat(adapter.nextPageNumber(document, Set.of(1, 2))).contains(3);
	}

	@Test
	void parsesArticleDateFromMainArticle() {
		Document document = articleDocument("""
				<span class="date">19:12 - 17 / 06 / 2026</span>
				<div class="content">
				  <p>The article paragraph is intentionally long enough for this fixture.</p>
				</div>
				""");

		assertThat(adapter.parseArticleDate(document, articleCard()))
				.contains(OffsetDateTime.parse("2026-06-17T19:12:00+04:00"));
	}

	@Test
	void extractsArticleTextFromContentParagraphsAndKeepsTextAfterAds() {
		Document document = articleDocument("""
				<h1 class="title">Title must not be part of article text</h1>
				<div class="sectionTitle">Category must not be collected</div>
				<div class="content">
				  <div class="thumb"><img src="/images/main.jpg"></div>
				  <p>The first real article paragraph contains <strong>important highlighted text</strong> for readers.</p>
				  <script>window._ttzi = 'tracking script must not leak';</script>
				  <div class="AdviadNativeVideo">AdviadNativeVideo must not leak</div>
				  <div class="leftColumnBanner"><p>Banner paragraph must be ignored completely.</p></div>
				  <p>The second real article paragraph appears after advertisement blocks and must not be lost.</p>
				</div>
				""");

		String text = adapter.extractArticleText(document);

		assertThat(text)
				.contains("The first real article paragraph contains important highlighted text")
				.contains("The second real article paragraph appears after advertisement blocks")
				.doesNotContain("Title must not be part")
				.doesNotContain("Category must not be collected")
				.doesNotContain("window._ttzi")
				.doesNotContain("AdviadNativeVideo")
				.doesNotContain("Banner paragraph");
	}

	@Test
	void extractsOnlyMainThumbImage() {
		Document document = articleDocument("""
				<div class="content">
				  <div class="thumb"><img src="/images/2026/06/17/20260617191207105/thumb.jpg?2026-06-17+19%3A17%3A34"></div>
				  <p>The article paragraph is intentionally long enough for this fixture.</p>
				  <div class="leftColumnBanner"><img src="/images/banner.jpg"></div>
				  <div class="share"><img src="/images/social-icon.png"></div>
				</div>
				<aside><img src="/images/sidebar.jpg"></aside>
				""");

		List<ScrapedMediaDTO> media = adapter.extractMedia(document, articleCard());

		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://1news.az/images/2026/06/17/20260617191207105/thumb.jpg?2026-06-17+19:17:34");
		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE);
	}

	private Element searchResult(String url) {
		return Jsoup.parse("""
				<div class="gsc-webResult gsc-result">
				  <div class="gs-title">
				    <a href="%s">Title</a>
				  </div>
				  <div class="gs-snippet">Snippet</div>
				</div>
				""".formatted(url), "https://1news.az/az")
				.selectFirst(".gsc-result");
	}

	private Document articleDocument(String body) {
		return Jsoup.parse("""
				<html>
				  <body>
				    <article class="mainArticle">
				      %s
				    </article>
				  </body>
				</html>
				""".formatted(body), "https://1news.az/az/news/20260617191207105-title");
	}

	private OneNewsAzSearchResultCard articleCard() {
		return new OneNewsAzSearchResultCard(
				"https://1news.az/az/news/20260617191207105-Yasamaldaki-yanginla-bagli-cinayet-ishi-mehkeme-baxishina-verildi",
				"Example title",
				null,
				OffsetDateTime.parse("2026-06-17T19:12:00+04:00"),
				null,
				1
		);
	}
}
