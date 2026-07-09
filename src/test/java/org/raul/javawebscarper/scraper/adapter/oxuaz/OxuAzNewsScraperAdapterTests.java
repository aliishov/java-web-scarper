package org.raul.javawebscarper.scraper.adapter.oxuaz;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OxuAzNewsScraperAdapterTests {

	private final OxuAzNewsScraperAdapter adapter = new OxuAzNewsScraperAdapter(
			null,
			new OxuAzDateParser(ZoneId.of("Asia/Baku"))
	);

	@Test
	void supportsOxuAzSource() {
		Source source = Source.builder()
				.code("OXU_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("OXU_AZ");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void supportsOxuAzBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://oxu.az/")
				.build();

		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void buildsFallbackSearchUrl() {
		assertThat(adapter.searchUrl("court")).isEqualTo("https://oxu.az/all?query=court");
	}

	@Test
	void extractsResultCardUrlDateTitleAndThumbnail() {
		Element card = Jsoup.parse("""
				<div class="col-12 col-sm-6 col-md-6 col-lg-6 rt-news-item"
				     data-url="https://oxu.az/cemiyyet/arzum-un-hokmu-texire-salindi">
				  <a class="post-item-img" href="/fallback">
				    <img src="/uploads/thumbnail.webp">
				  </a>
				  <h2 class="post-item-title">
				    <a href="/ignored">Court hearing was postponed</a>
				  </h2>
				  <div class="post-item-meta"><span>25.06.2026 / 12:40</span></div>
				</div>
				""", "https://oxu.az/")
				.selectFirst(".rt-news-item");

		Optional<OxuAzSearchResultCard> result = adapter.parseResultCard(card);

		assertThat(result).isPresent();
		assertThat(result.get().postUrl())
				.isEqualTo("https://oxu.az/cemiyyet/arzum-un-hokmu-texire-salindi");
		assertThat(result.get().title()).isEqualTo("Court hearing was postponed");
		assertThat(result.get().postDate()).isEqualTo(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
		assertThat(result.get().thumbnailUrl()).isEqualTo("https://oxu.az/uploads/thumbnail.webp");
	}

	@Test
	void resolvesRelativeResultCardUrl() {
		Element card = Jsoup.parse("""
				<div class="rt-news-item" data-url="/cemiyyet/example">
				  <h2 class="post-item-title"><a href="/ignored">Example</a></h2>
				  <div class="post-item-meta"><span>25.06.2026 12:40</span></div>
				</div>
				""", "https://oxu.az/")
				.selectFirst(".rt-news-item");

		Optional<OxuAzSearchResultCard> result = adapter.parseResultCard(card);

		assertThat(result).isPresent();
		assertThat(result.get().postUrl()).isEqualTo("https://oxu.az/cemiyyet/example");
	}

	@Test
	void extractsArticleTextFromScopedBodyParagraphs() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <article>
				      <h1>Title must not be part of body text</h1>
				      <div class="news-inner__desc">
				        <p>Dili secin</p>
				        <p>The first article paragraph has useful text and should be kept by the extractor.</p>
				        <p>The second article paragraph is also useful and must stay in the result text.</p>
				        <div class="related-news">
				          <p>Related article text should be ignored even when it is long enough to look like content.</p>
				        </div>
				      </div>
				    </article>
				  </body>
				</html>
				""", "https://oxu.az/cemiyyet/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text)
				.contains("The first article paragraph has useful text")
				.contains("The second article paragraph is also useful")
				.doesNotContain("Dili secin")
				.doesNotContain("Title must not be part")
				.doesNotContain("Related article text should be ignored");
	}

	@Test
	void fallsBackToMetaDescriptionWhenArticleBodyIsMissing() {
		Document document = Jsoup.parse("""
				<html>
				  <head>
				    <meta property="og:description" content="This fallback article description is long enough to be used when oxu.az article content is missing.">
				  </head>
				  <body></body>
				</html>
				""", "https://oxu.az/cemiyyet/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text).contains("fallback article description is long enough");
	}

	@Test
	void extractsArticleDateFromMetaBeforeCardFallbackWouldBeUsed() {
		Document document = Jsoup.parse("""
				<html>
				  <head>
				    <meta property="article:published_time" content="2026-06-25T12:40:00+04:00">
				  </head>
				  <body></body>
				</html>
				""", "https://oxu.az/cemiyyet/example");

		assertThat(adapter.parseArticleDate(document))
				.contains(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
	}

	@Test
	void extractsArticleMediaOnlyFromMainArticleImage() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <article>
				      <div class="news-inner__image">
				        <img src="/uploads/article-main.webp">
				      </div>
				      <div class="news-inner__desc">
				        <p>The article text is intentionally long enough for the fixture.</p>
				      </div>
				      <div class="related-news">
				        <img src="/uploads/related.webp">
				      </div>
				    </article>
				    <aside class="sidebar">
				      <img src="/uploads/sidebar.webp">
				    </aside>
				  </body>
				</html>
				""", "https://oxu.az/cemiyyet/example");

		List<ScrapedMediaDTO> media = adapter.extractMedia(document, articleCard());

		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://oxu.az/uploads/article-main.webp");
		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE);
	}

	@Test
	void usesSearchThumbnailWhenArticleMainImageIsMissing() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <article>
				      <div class="news-inner__desc">
				        <p>The article text is intentionally long enough for the fixture.</p>
				      </div>
				    </article>
				  </body>
				</html>
				""", "https://oxu.az/cemiyyet/example");

		List<ScrapedMediaDTO> media = adapter.extractMedia(document, articleCard());

		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://oxu.az/uploads/thumbnail.webp");
	}

	@Test
	void doesNotCollectCategoryText() {
		Document document = Jsoup.parse("""
				<html>
				  <body>
				    <article>
				      <div class="category">Cemiyyet category must not be stored</div>
				      <div class="news-inner__desc">
				        <p>The article paragraph has enough useful text to be accepted by the extractor.</p>
				      </div>
				    </article>
				  </body>
				</html>
				""", "https://oxu.az/cemiyyet/example");

		String text = adapter.extractArticleText(document, articleCard());

		assertThat(text)
				.contains("The article paragraph has enough useful text")
				.doesNotContain("Cemiyyet category");
	}

	private OxuAzSearchResultCard articleCard() {
		return new OxuAzSearchResultCard(
				"https://oxu.az/cemiyyet/example",
				"Example title",
				OffsetDateTime.parse("2026-06-25T12:40:00+04:00"),
				"https://oxu.az/uploads/thumbnail.webp"
		);
	}
}
