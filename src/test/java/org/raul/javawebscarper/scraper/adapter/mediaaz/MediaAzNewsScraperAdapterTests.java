package org.raul.javawebscarper.scraper.adapter.mediaaz;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MediaAzNewsScraperAdapterTests {

	private final MediaAzNewsScraperAdapter adapter = new MediaAzNewsScraperAdapter(
			null,
			new MediaAzDateParser(ZoneId.of("Asia/Baku"))
	);

	@Test
	void supportsMediaAzSource() {
		Source source = Source.builder()
				.code("MEDIA_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("MEDIA_AZ");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void supportsMediaAzBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://media.az/")
				.build();

		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void buildsFallbackSearchUrl() {
		assertThat(adapter.searchUrl("court")).isEqualTo("https://media.az/search?query=court");
	}

	@Test
	void buildsDatedSearchUrlWithDateEnd() {
		String url = adapter.datedSearchUrl(
				"court",
				LocalDate.parse("2026-06-25"),
				LocalDate.parse("2026-06-25"),
				true
		);

		assertThat(url)
				.isEqualTo("https://media.az/search?query=court&date_start=2026-06-25&date_end=2026-06-25&category=&sort_type=0");
	}

	@Test
	void buildsDatedSearchUrlWithEmptyDateEndFallback() {
		String url = adapter.datedSearchUrl(
				"court",
				LocalDate.parse("2026-06-25"),
				LocalDate.parse("2026-06-25"),
				false
		);

		assertThat(url)
				.isEqualTo("https://media.az/search?query=court&date_start=2026-06-25&date_end=&category=&sort_type=0");
	}

	@Test
	void extractsSearchCardFromTimestamp() {
		Element card = Jsoup.parse("""
				<div class="col-md-4 post-block" data-timestamp="2026-06-25 12:40:00">
				  <a target="_blank" href="https://media.az/politika/example" class="news__item">
				    <div class="news__image"><img src="/images/thumb.webp"></div>
				    <h3 class="news__title">Example title</h3>
				    <div class="news__date">25.06.2026 12:40</div>
				  </a>
				</div>
				""", "https://media.az/")
				.selectFirst(".post-block");

		Optional<MediaAzSearchResultCard> result = adapter.parseResultCard(card);

		assertThat(result).isPresent();
		assertThat(result.get().postUrl()).isEqualTo("https://media.az/politika/example");
		assertThat(result.get().title()).isEqualTo("Example title");
		assertThat(result.get().postDate()).isEqualTo(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
		assertThat(result.get().thumbnailUrl()).isEqualTo("https://media.az/images/thumb.webp");
	}

	@Test
	void extractsSearchCardWithVisibleDateFallback() {
		Element card = Jsoup.parse("""
				<div class="col-md-4 post-block" data-timestamp="">
				  <a target="_blank" href="/politika/example" class="news__item">
				    <h3 class="news__title">Example title</h3>
				    <div class="news__date">25.06.2026 12:40</div>
				  </a>
				</div>
				""", "https://media.az/")
				.selectFirst(".post-block");

		Optional<MediaAzSearchResultCard> result = adapter.parseResultCard(card);

		assertThat(result).isPresent();
		assertThat(result.get().postUrl()).isEqualTo("https://media.az/politika/example");
		assertThat(result.get().postDate()).isEqualTo(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
	}

	@Test
	void parsesArticleDateFromNewsInnerInfo() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <div class="news-inner__body rt-news-item">
				      <ul class="news-inner__info">
				        <li>Politics</li>
				        <li>25.06.2026 12:40</li>
				      </ul>
				    </div>
				  </body>
				</html>
				""", "https://media.az/politika/example");

		assertThat(adapter.parseArticleDate(document))
				.contains(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
	}

	@Test
	void extractsArticleTextOnlyFromNewsInnerDescParagraphs() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <div class="news-inner__body rt-news-item">
				      <h1 class="news-inner__title">Title must not be part of text</h1>
				      <div class="category">Politics category must not be stored</div>
				      <div class="news-inner__desc">
				        <p>The first article paragraph is long enough to be accepted by the extractor.</p>
				        <p>The second article paragraph is also useful and must stay in the result text.</p>
				        <div class="related-news">
				          <p>Related article text should be ignored even when it looks long enough.</p>
				        </div>
				        <div class="seo__tags">SEO tags must be ignored</div>
				      </div>
				    </div>
				  </body>
				</html>
				""", "https://media.az/politika/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text)
				.contains("The first article paragraph is long enough")
				.contains("The second article paragraph is also useful")
				.doesNotContain("Title must not be part")
				.doesNotContain("Politics category")
				.doesNotContain("Related article text should be ignored")
				.doesNotContain("SEO tags");
	}

	@Test
	void fallsBackToMetaDescriptionWhenArticleBodyIsMissing() {
		Document document = Jsoup.parse("""
				<html>
				  <head>
				    <meta property="og:description" content="This fallback article description is long enough to be used when media.az article content is missing.">
				  </head>
				  <body></body>
				</html>
				""", "https://media.az/politika/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text).contains("fallback article description is long enough");
	}

	@Test
	void extractsMediaOnlyFromNewsInnerImage() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <div class="news-inner__body rt-news-item">
				      <div class="news-inner__image">
				        <img src="/uploads/article-main.webp">
				      </div>
				      <div class="news-inner__desc">
				        <p>The article paragraph is intentionally long enough for this fixture.</p>
				        <img src="/uploads/body-image-should-not-be-used.webp">
				      </div>
				    </div>
				    <aside class="sidebar">
				      <img src="/uploads/sidebar.webp">
				    </aside>
				  </body>
				</html>
				""", "https://media.az/politika/example");

		List<ScrapedMediaDTO> media = adapter.extractMedia(document, articleCard());

		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://media.az/uploads/article-main.webp");
		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE);
	}

	@Test
	void detectsCloudflareChallengePage() {
		String html = """
				<html>
				  <head><title>Just a moment...</title></head>
				  <body>
				    <script src="/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1"></script>
				  </body>
				</html>
				""";

		assertThat(MediaAzScraperSupport.isLikelyAntiBotPage(html)).isTrue();
	}

	@Test
	void doesNotTreatNormalMediaAzPageAsAntiBotChallenge() {
		String html = """
				<html>
				  <body>
				    <div class="post-block" data-timestamp="2026-06-25 12:40:00">
				      <a class="news__item" href="https://media.az/politika/example">Example</a>
				    </div>
				  </body>
				</html>
				""";

		assertThat(MediaAzScraperSupport.isLikelyAntiBotPage(html)).isFalse();
	}

	private MediaAzSearchResultCard articleCard() {
		return new MediaAzSearchResultCard(
				"https://media.az/politika/example",
				"Example title",
				OffsetDateTime.parse("2026-06-25T12:40:00+04:00"),
				"https://media.az/images/thumb.webp"
		);
	}
}
