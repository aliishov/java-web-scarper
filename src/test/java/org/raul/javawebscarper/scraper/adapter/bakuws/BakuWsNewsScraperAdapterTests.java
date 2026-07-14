package org.raul.javawebscarper.scraper.adapter.bakuws;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BakuWsNewsScraperAdapterTests {

	private final BakuWsNewsScraperAdapter adapter = new BakuWsNewsScraperAdapter(
			null,
			new BakuWsDateParser(ZoneId.of("Asia/Baku"))
	);

	@Test
	void supportsBakuWsSource() {
		Source source = Source.builder()
				.code("BAKU_WS")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("BAKU_WS");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void supportsBakuWsBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://baku.ws/")
				.build();

		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void buildsEncodedFallbackSearchUrl() {
		assertThat(adapter.searchUrl("M\u0259hk\u0259m\u0259"))
				.isEqualTo("https://baku.ws/search?query=M%C9%99hk%C9%99m%C9%99");
	}

	@Test
	void extractsSearchCardUsingDataUrlPriorityAndScopedThumbnail() {
		Document document = Jsoup.parse("""
				<div class="post-item">
				  <div class="post-item-img">
				    <a href="https://baku.ws/incident/image-link-111">
				      <img src="/storage/photos/uploads/thumbs/list/example.webp">
				      <span class="post-item-date">
				        <span class="post-item-date-time">21:41</span>
				        <span class="post-item-date-day">27 iyun 2026</span>
				      </span>
				    </a>
				  </div>
				  <div class="post-item-content rt-news-item" data-url="https://baku.ws/diger/data-url-title-553631">
				    <h3 class="post-item-title">
				      <a href="https://baku.ws/politics/title-link-222">Search result title</a>
				    </h3>
				  </div>
				</div>
				<div class="cat-left-bnr"><img src="/images/banners/placeholder_home_right_az.jpg"></div>
				""", "https://baku.ws/search?query=court");

		BakuWsNewsScraperAdapter.CardParseResult parsed = adapter.parseResultCard(document.selectFirst(".post-item"), 4);

		assertThat(parsed.card()).isNotNull();
		assertThat(parsed.card().postUrl()).isEqualTo("https://baku.ws/diger/data-url-title-553631");
		assertThat(parsed.card().title()).isEqualTo("Search result title");
		assertThat(parsed.card().postDate()).isEqualTo(OffsetDateTime.parse("2026-06-27T21:41:00+04:00"));
		assertThat(parsed.card().thumbnailUrl())
				.isEqualTo("https://baku.ws/storage/photos/uploads/thumbs/list/example.webp");
		assertThat(parsed.card().scrollBatch()).isEqualTo(4);
	}

	@Test
	void skipsSearchAdsAndInvalidResultUrls() {
		Document document = Jsoup.parse("""
				<div class="cat-left-bnr">
				  <div class="post-item">
				    <div class="post-item-content" data-url="https://baku.ws/diger/ad-111"></div>
				  </div>
				</div>
				<div class="post-item">
				  <div class="post-item-content" data-url="https://baku.ws/tag/court"></div>
				  <span class="post-item-date-time">21:41</span>
				  <span class="post-item-date-day">27 iyun 2026</span>
				</div>
				""", "https://baku.ws/search?query=court");

		assertThat(adapter.parseResultCard(document.select(".post-item").get(0), 1).card()).isNull();
		assertThat(adapter.parseResultCard(document.select(".post-item").get(1), 1).card()).isNull();
	}

	@Test
	void extractsArticleTextFromPostDetailContentParagraphs() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <section class="news-detail">
				      <div class="post-detail post-detail-area">
				        <div class="post-detail-title"><h1>Title must not be part of body text</h1></div>
				        <div class="post-date">07 iyl 2026 11:45</div>
				        <div class="post-detail-content resize-area">
				          <p>Dili secin</p>
				          <p><strong>State service opened a criminal case after reviewing official materials in detail.</strong></p>
				          <p>The press office said the investigation continues and additional public information will be shared later.</p>
				          <div class="related-news">
				            <p>Related article text should be ignored even when it is long enough to look like content.</p>
				          </div>
				        </div>
				      </div>
				    </section>
				  </body>
				</html>
				""", "https://baku.ws/incident/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text)
				.contains("State service opened a criminal case")
				.contains("The press office said the investigation continues")
				.doesNotContain("Dili secin")
				.doesNotContain("Title must not be part")
				.doesNotContain("Related article text should be ignored");
	}

	@Test
	void fallsBackToMetaDescriptionWhenArticleContentIsMissing() {
		Document document = Jsoup.parse("""
				<html>
				  <head>
				    <meta property="og:description" content="This fallback article description is long enough to be used when article content is missing.">
				  </head>
				  <body></body>
				</html>
				""", "https://baku.ws/incident/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text).contains("fallback article description is long enough");
	}

	@Test
	void parsesArticleDateTimeFromPostDetailImageBlock() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <div class="post-date">
				      <span class="post-date-day">01</span>
				      <span class="post-date-month">iyn</span>
				      <span class="post-date-year">2026</span>
				      <span class="post-date-time">10:15</span>
				    </div>
				    <div class="post-detail post-detail-area">
				      <div class="post-detail-top">
				        <div class="post-detail-img">
				          <img src="/storage/photos/2026/07/article.webp">
				          <div class="post-date">
				            <span class="post-date-inner">
				              <span class="post-date-day">07</span>
				              <span class="post-date-month">iyl</span>
				            </span>
				            <span class="post-date-year">2026</span>
				            <span class="post-date-time">23:36</span>
				          </div>
				        </div>
				      </div>
				    </div>
				  </body>
				</html>
				""", "https://baku.ws/incident/example");

		assertThat(adapter.parseArticleDate(document))
				.contains(OffsetDateTime.parse("2026-07-07T23:36:00+04:00"));
	}

	@Test
	void extractsArticleMediaOnlyFromPostDetailMainImage() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <div class="post-detail post-detail-area">
				      <div class="post-detail-top">
				        <div class="post-detail-img">
				          <img src="/storage/photos/2026/07/article.webp">
				        </div>
				      </div>
				      <div class="post-detail-content resize-area">
				        <img src="https://avatars.mds.yandex.net/ad-image.jpg">
				        <img src="/storage/photos/2026/07/content-image-should-not-be-used.webp">
				      </div>
				    </div>
				    <aside class="sidebar">
				      <img src="/storage/photos/2026/07/sidebar.webp">
				    </aside>
				    <div class="related-news">
				      <img src="/storage/photos/2026/07/related.webp">
				    </div>
				  </body>
				</html>
				""", "https://baku.ws/incident/example");

		List<ScrapedMediaDTO> media = adapter.extractMedia(document, articleCard());

		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://baku.ws/storage/photos/2026/07/article.webp");
		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE);
	}

	@Test
	void usesSearchThumbnailWhenArticleMainImageIsMissing() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <div class="post-detail post-detail-area">
				      <div class="post-detail-content resize-area">
				        <img src="/storage/photos/2026/07/content-image-should-not-be-used.webp">
				      </div>
				    </div>
				  </body>
				</html>
				""", "https://baku.ws/incident/example");

		List<ScrapedMediaDTO> media = adapter.extractMedia(document, articleCard());

		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://baku.ws/storage/photos/thumbnail.webp");
	}

	@Test
	void removesYandexAdsFromArticleText() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <div class="post-detail post-detail-area">
				      <div class="post-detail-content resize-area">
				        <p><strong>Main article paragraph has useful text and should be preserved by the extractor.</strong></p>
				        <div id="yandex_rtb_R-A-13706460-6-555577" data-name="adWrapper">
				          <p>Ad yandex colizeumarena https://avatars.mds.yandex.net/banner.jpg</p>
				          <img src="https://avatars.mds.yandex.net/ad.jpg">
				        </div>
				        <script>window.yaContextCb = window.yaContextCb || [];</script>
				        <ins>Ad</ins>
				        <p>The second article paragraph is also useful and must stay in the result text.</p>
				      </div>
				    </div>
				  </body>
				</html>
				""", "https://baku.ws/incident/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text)
				.contains("Main article paragraph has useful text")
				.contains("The second article paragraph is also useful")
				.doesNotContain("yandex")
				.doesNotContain("colizeumarena")
				.doesNotContain("avatars.mds.yandex.net")
				.doesNotContain("Ad");
	}

	private BakuWsSearchResultCard articleCard() {
		return new BakuWsSearchResultCard(
				"https://baku.ws/incident/example",
				"Example title",
				OffsetDateTime.parse("2026-07-07T11:45:00+04:00"),
				"https://baku.ws/storage/photos/thumbnail.webp",
				2
		);
	}
}
