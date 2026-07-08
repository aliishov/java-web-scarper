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
	void extractsArticleTextFromPostDetailParagraphs() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <section class="news-detail">
				      <div class="post-detail post-detail-area">
				        <div class="post-detail-title"><h1>Title must not be part of body text</h1></div>
				        <div class="post-date">07 iyl 2026 11:45</div>
				        <p>Dili seçin</p>
				        <p>Səfərbərlik və Hərbi Xidmətə Çağırış üzrə Dövlət Xidmətinin vəzifəli şəxsi barəsində cinayət işi açılıb.</p>
				        <p>Bu barədə BAKU.WS-ə Baş Prokurorluğun Mətbuat xidməti məlumat yayıb və istintaqın davam etdiyi bildirilib.</p>
				        <div class="related-news">
				          <p>Related article text should be ignored even when it is long enough to look like content.</p>
				        </div>
				      </div>
				    </section>
				  </body>
				</html>
				""", "https://baku.ws/incident/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text)
				.contains("Səfərbərlik və Hərbi Xidmətə Çağırış üzrə Dövlət Xidmətinin")
				.contains("Baş Prokurorluğun Mətbuat xidməti")
				.doesNotContain("Dili seçin")
				.doesNotContain("Title must not be part")
				.doesNotContain("Related article text should be ignored");
	}

	@Test
	void fallsBackToMetaDescriptionWhenArticleRootIsMissing() {
		Document document = Jsoup.parse("""
				<html>
				  <head>
				    <meta property="og:description" content="Bu fallback mətn kifayət qədər uzundur və article root tapılmadıqda istifadə olunmalıdır.">
				  </head>
				  <body></body>
				</html>
				""", "https://baku.ws/incident/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text).contains("fallback mətn kifayət qədər uzundur");
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
				.containsExactly(
						"https://baku.ws/storage/photos/2026/07/article.webp"
				);
		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE);
	}

	@Test
	void usesSearchThumbnailWhenArticleHasNoAllowedMedia() {
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

	private BakuWsSearchResultCard articleCard() {
		return new BakuWsSearchResultCard(
				"https://baku.ws/incident/example",
				"Example title",
				OffsetDateTime.parse("2026-07-07T11:45:00+04:00"),
				"https://baku.ws/storage/photos/thumbnail.webp"
		);
	}
}
