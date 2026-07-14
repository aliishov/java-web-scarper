package org.raul.javawebscarper.scraper.adapter.qafqazinfoaz;

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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QafqazInfoAzNewsScraperAdapterTests {

	private final QafqazInfoAzNewsScraperAdapter adapter = new QafqazInfoAzNewsScraperAdapter(
			null,
			new QafqazInfoAzDateParser(ZoneId.of("Asia/Baku"))
	);

	@Test
	void supportsQafqazInfoAzSource() {
		Source source = Source.builder()
				.code("QAFQAZINFO_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("QAFQAZINFO_AZ");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void buildsEncodedFallbackSearchUrl() {
		assertThat(adapter.searchUrl("Məhkəmə"))
				.isEqualTo("https://qafqazinfo.az/news/search?keyword=M%C9%99hk%C9%99m%C9%99");
	}

	@Test
	void extractsSearchCardTitleUrlAndScopedThumbnail() {
		Document document = Jsoup.parse("""
				<div class="col-lg-4 col-md-4">
				  <a href="https://qafqazinfo.az/news/detail/kesisden-bele-rusvet-alib-7-il-hebs-verildi-513531">
				    <img class="img-responsive" src="/uploads/1782500137/koreya.png">
				    <h4 class="hemcinin">Keşişdən belə rüşvət alıb <span>- 7 il həbs verildi</span></h4>
				  </a>
				</div>
				<img class="img-responsive" src="https://qafqazinfo.az/banners/dynamic_banners/ad.png">
				""", "https://qafqazinfo.az/");

		Element link = document.selectFirst("a[href*='/news/detail/']");
		QafqazInfoAzNewsScraperAdapter.SearchCardParseResult parsed = adapter.parseResultCard(link, 2);

		assertThat(parsed.card()).isNotNull();
		assertThat(parsed.card().postUrl())
				.isEqualTo("https://qafqazinfo.az/news/detail/kesisden-bele-rusvet-alib-7-il-hebs-verildi-513531");
		assertThat(parsed.card().title()).isEqualTo("Keşişdən belə rüşvət alıb - 7 il həbs verildi");
		assertThat(parsed.card().thumbnailUrl()).isEqualTo("https://qafqazinfo.az/uploads/1782500137/koreya.png");
		assertThat(parsed.card().searchPageNumber()).isEqualTo(2);
	}

	@Test
	void rejectsCategorySearchAndNonArticleUrls() {
		Document document = Jsoup.parse("""
				<a href="https://qafqazinfo.az/news/category/dunya-6"><h4 class="hemcinin">Dünya</h4></a>
				<a href="https://qafqazinfo.az/news/search?keyword=test"><h4 class="hemcinin">Search</h4></a>
				<a href="https://example.com/news/detail/example-123"><h4 class="hemcinin">External</h4></a>
				""", "https://qafqazinfo.az/");

		assertThat(adapter.parseResultCard(document.select("a").get(0), 1).card()).isNull();
		assertThat(adapter.parseResultCard(document.select("a").get(1), 1).card()).isNull();
		assertThat(adapter.parseResultCard(document.select("a").get(2), 1).card()).isNull();
	}

	@Test
	void resolvesNextNumericPageBeforeSuspiciousNextLink() {
		Document document = Jsoup.parse("""
				<div class="pager">
				  <ul id="yw2" class="yiiPager">
				    1
				    <li class="page"><a href="?keyword=Məhkəmə&page=2">2</a></li>
				    <li class="next"><a href="?keyword=Məhkəmə&page=1">Sonrakı</a></li>
				  </ul>
				</div>
				""", "https://qafqazinfo.az/news/search?keyword=M%C9%99hk%C9%99m%C9%99");

		assertThat(adapter.nextPageUrl(
				document,
				"https://qafqazinfo.az/news/search?keyword=M%C9%99hk%C9%99m%C9%99",
				"Məhkəmə",
				new LinkedHashSet<>(),
				Set.of(1)
		)).contains("https://qafqazinfo.az/news/search?keyword=Məhkəmə&page=2");
	}

	@Test
	void doesNotLoopToVisitedOrSameNextPage() {
		Document document = Jsoup.parse("""
				<div class="pager">
				  <ul class="yiiPager">
				    <li class="next"><a href="?keyword=Məhkəmə&page=1">Sonrakı</a></li>
				  </ul>
				</div>
				""", "https://qafqazinfo.az/news/search?keyword=M%C9%99hk%C9%99m%C9%99");

		assertThat(adapter.nextPageUrl(
				document,
				"https://qafqazinfo.az/news/search?keyword=M%C9%99hk%C9%99m%C9%99",
				"Məhkəmə",
				new LinkedHashSet<>(),
				Set.of(1)
		)).isEmpty();
	}

	@Test
	void findsArticleRootAmongMultiplePanelBodies() {
		Document document = articleDocument("""
				<div class="panel-body"><p>sidebar</p></div>
				<div class="panel-body">
				  <h1>Keşişdən belə rüşvət alıb <span>- 7 il həbs verildi</span></h1>
				  <img class="img-responsive" src="/uploads/main.png">
				  <div class="news-time"><time datetime="06.26.2026 | 22:55">26.06.2026 | 22:55</time></div>
				  <div class="panel-body news_text"><p>Bu article paragraph kifayət qədər uzundur.</p></div>
				</div>
				""");

		Element root = adapter.findArticleRoot(document).orElseThrow();

		assertThat(root.selectFirst("h1").text()).contains("Keşişdən belə rüşvət alıb");
	}

	@Test
	void parsesArticleVisibleDateBeforeDatetimeFallback() {
		Element root = articleRoot("""
				<h1>Title</h1>
				<div class="news-time"><time datetime="06.25.2026 | 22:55">26.06.2026 | 22:55</time></div>
				<div class="panel-body news_text"><p>Bu article paragraph kifayət qədər uzundur.</p></div>
				""");

		assertThat(adapter.parseArticleDate(root, articleCard()))
				.hasValueSatisfying(result -> {
					assertThat(result.date()).isEqualTo(OffsetDateTime.parse("2026-06-26T22:55:00+04:00"));
					assertThat(result.conflict()).isTrue();
				});
	}

	@Test
	void extractsOnlyArticleParagraphs() {
		Element root = articleRoot("""
				<h1>Title</h1>
				<div class="news-time"><time>26.06.2026 | 22:55</time></div>
				<div class="panel-body news_text">
				  <p></p>
				  <p><strong>Cənubi Koreyada məhkəmə barədə əsas xəbər mətni burada yerləşir.</strong></p>
				  <p>“Qafqazinfo” xəbər verir ki, ikinci paragraph yalnız article body daxilindədir.</p>
				  <div class="social-buttons">Facebook Telegram Whatsapp</div>
				  <iframe src="https://qafqazinfo.az/banners/dynamic_banners/ad"></iframe>
				</div>
				<div class="panel-body">Oxunma sayı: 100</div>
				""");

		String text = adapter.extractArticleText(root);

		assertThat(text).contains("Cənubi Koreyada məhkəmə barədə əsas xəbər mətni");
		assertThat(text).contains("ikinci paragraph yalnız article body daxilindədir");
		assertThat(text).doesNotContain("Facebook");
		assertThat(text).doesNotContain("Oxunma sayı");
		assertThat(text).doesNotContain("dynamic_banners");
	}

	@Test
	void extractsOnlyMainImageAndIgnoresBannerOrThumbnailWhenPresent() {
		Element root = articleRoot("""
				<h1>Title</h1>
				<img class="img-responsive" src="/uploads/1782500137/koreya.png">
				<div class="news-time"><time>26.06.2026 | 22:55</time></div>
				<div class="panel-body news_text">
				  <p>Bu article paragraph kifayət qədər uzundur və image testini tamamlayır.</p>
				  <img class="img-responsive" src="/uploads/body.png">
				  <iframe src="https://qafqazinfo.az/banners/dynamic_banners/ad"></iframe>
				</div>
				""");

		QafqazInfoAzNewsScraperAdapter.MediaExtractionResult result = adapter.extractMedia(root, articleCard());

		assertThat(result.mainImagesCollected()).isEqualTo(1);
		assertThat(result.media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://qafqazinfo.az/uploads/1782500137/koreya.png");
		assertThat(result.media()).extracting(ScrapedMediaDTO::mediaType).containsExactly(MediaType.IMAGE);
	}

	@Test
	void fallsBackToSearchThumbnailWhenMainImageMissing() {
		Element root = articleRoot("""
				<h1>Title</h1>
				<div class="news-time"><time>26.06.2026 | 22:55</time></div>
				<div class="panel-body news_text">
				  <p>Bu article paragraph kifayət qədər uzundur və thumbnail fallback testidir.</p>
				</div>
				""");

		QafqazInfoAzNewsScraperAdapter.MediaExtractionResult result = adapter.extractMedia(root, articleCard());

		assertThat(result.mainImagesCollected()).isZero();
		assertThat(result.media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://qafqazinfo.az/uploads/thumb.png");
	}

	private Document articleDocument(String body) {
		return Jsoup.parse("""
				<html><body>
				%s
				</body></html>
				""".formatted(body), "https://qafqazinfo.az/news/detail/example-513531");
	}

	private Element articleRoot(String body) {
		return articleDocument("""
				<div class="panel-body">
				%s
				</div>
				""".formatted(body)).selectFirst(".panel-body");
	}

	private QafqazInfoAzSearchResultCard articleCard() {
		return new QafqazInfoAzSearchResultCard(
				"https://qafqazinfo.az/news/detail/example-513531",
				"Title",
				"https://qafqazinfo.az/uploads/thumb.png",
				3
		);
	}
}
