package org.raul.javawebscarper.scraper.adapter.caliberaz;

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

import static org.assertj.core.api.Assertions.assertThat;

class CaliberAzNewsScraperAdapterTests {

	private final CaliberAzNewsScraperAdapter adapter = new CaliberAzNewsScraperAdapter(
			null,
			new CaliberAzDateParser(ZoneId.of("Asia/Baku"))
	);

	@Test
	void supportsCaliberAzSource() {
		Source source = Source.builder()
				.code("CALIBER_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("CALIBER_AZ");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void buildsPathEncodedFallbackSearchUrl() {
		assertThat(adapter.searchUrl("Суд"))
				.isEqualTo("https://caliber.az/search/%D0%A1%D1%83%D0%B4");
		assertThat(adapter.searchUrl("Ильхам Алиев"))
				.isEqualTo("https://caliber.az/search/%D0%98%D0%BB%D1%8C%D1%85%D0%B0%D0%BC%20%D0%90%D0%BB%D0%B8%D0%B5%D0%B2");
	}

	@Test
	void extractsSearchCardAndIgnoresCategoryLink() {
		Element card = Jsoup.parse("""
				<div class="float_block">
				  <a href="https://caliber.az/post/prokuratura-trebuet-smertnoj-kazni-dlya-eks-prezidenta-yuzhnoj-korei">
				    <div class="float_block_image_block">
				      <div class="float_block_image" style="background-image:url(https://caliber.az/media/photos/normal/fb448cb35baa669d1ef5bb7bad3bfd67.webp)"></div>
				    </div>
				  </a>
				  <a href="https://caliber.az/post/prokuratura-trebuet-smertnoj-kazni-dlya-eks-prezidenta-yuzhnoj-korei">
				    <h2 class="float_block_title">Прокуратура требует смертной казни для экс-президента Южной Кореи</h2>
				  </a>
				  <div class="float_block_bottom">
				    <a href="https://caliber.az/category/mir"><div class="float_block_category">МИР</div></a>
				    <div class="float_block_time">25 Июня 2026 17:24</div>
				  </div>
				</div>
				""", "https://caliber.az/")
				.selectFirst(".float_block");

		CaliberAzNewsScraperAdapter.SearchCardParseResult parsed = adapter.parseResultCard(card, 2);

		assertThat(parsed.card()).isNotNull();
		assertThat(parsed.card().postUrl())
				.isEqualTo("https://caliber.az/post/prokuratura-trebuet-smertnoj-kazni-dlya-eks-prezidenta-yuzhnoj-korei");
		assertThat(parsed.card().title()).contains("Прокуратура требует");
		assertThat(parsed.card().searchDate()).isEqualTo(OffsetDateTime.parse("2026-06-25T17:24:00+04:00"));
		assertThat(parsed.card().thumbnailUrl())
				.isEqualTo("https://caliber.az/media/photos/normal/fb448cb35baa669d1ef5bb7bad3bfd67.webp");
		assertThat(parsed.card().scrollBatch()).isEqualTo(2);
	}

	@Test
	void rejectsNonPostSearchCardUrl() {
		Element card = Jsoup.parse("""
				<div class="float_block">
				  <a href="https://caliber.az/category/mir"><h2 class="float_block_title">Category</h2></a>
				  <div class="float_block_time">25 Июня 2026 17:24</div>
				</div>
				""", "https://caliber.az/")
				.selectFirst(".float_block");

		CaliberAzNewsScraperAdapter.SearchCardParseResult parsed = adapter.parseResultCard(card, 1);

		assertThat(parsed.card()).isNull();
		assertThat(parsed.skipReason())
				.isEqualTo(CaliberAzNewsScraperAdapter.SearchCardSkipReason.EXTERNAL_OR_INVALID_URL);
	}

	@Test
	void parsesArticleDateBeforeSearchCardFallback() {
		Document document = articleDocument("""
				<div class="post_time">25 Июня 2026 17:24</div>
				<div class="post_body">
				  <p>The article paragraph is intentionally long enough for the extractor fixture.</p>
				</div>
				""");

		assertThat(adapter.parseArticleDate(document, articleCard()))
				.contains(OffsetDateTime.parse("2026-06-25T17:24:00+04:00"));
	}

	@Test
	void fallsBackToSearchCardDateWhenArticleDateMissing() {
		Document document = articleDocument("""
				<div class="post_body">
				  <p>The article paragraph is intentionally long enough for the extractor fixture.</p>
				</div>
				""");

		assertThat(adapter.parseArticleDate(document, articleCard()))
				.contains(OffsetDateTime.parse("2026-06-20T10:15:00+04:00"));
	}

	@Test
	void extractsArticleTextOnlyFromPostBodyParagraphs() {
		Document document = articleDocument("""
				<div class="post_cover" style="background-image:url(https://caliber.az/media/photos/original/a.webp)"></div>
				<h1 class="post_title">Title must not be included in text</h1>
				<div class="post_categoty">Category must not be collected</div>
				<div class="post_time">25 Июня 2026 17:24</div>
				<div class="post_body">
				  <p><strong>Первый абзац</strong> должен попасть в итоговый текст статьи.</p>
				  <div class="banner"><p>Banner paragraph must be removed.</p></div>
				  <p>Второй абзац содержит <em>важные детали</em> и тоже остается.</p>
				</div>
				<div class="post_signature">Caliber.Az</div>
				<div class="post_hits">Просмотров: 179</div>
				""");

		String text = adapter.extractArticleText(document);

		assertThat(text)
				.contains("Первый абзац должен попасть")
				.contains("Второй абзац содержит важные детали")
				.doesNotContain("Title must not")
				.doesNotContain("Category must")
				.doesNotContain("Banner paragraph")
				.doesNotContain("Caliber.Az")
				.doesNotContain("Просмотров");
	}

	@Test
	void extractsCoverAndBodyMediaWithoutDuplicates() {
		Document document = articleDocument("""
				<div class="post_cover" style="background-image: url('https://caliber.az/media/photos/original/cover.webp')"></div>
				<div class="post_body">
				  <p>The article paragraph is intentionally long enough for the extractor fixture.</p>
				  <img src="https://caliber.az/media/photos/original/body.webp">
				  <img src="https://caliber.az/media/photos/original/body.webp">
				  <img src="https://caliber.az/templates/Default/api/zoom.png">
				</div>
				<aside><img src="https://caliber.az/media/photos/original/sidebar.webp"></aside>
				""");

		CaliberAzNewsScraperAdapter.MediaExtractionResult result = adapter.extractMedia(document, articleCard());

		assertThat(result.media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly(
						"https://caliber.az/media/photos/original/cover.webp",
						"https://caliber.az/media/photos/original/body.webp"
				);
		assertThat(result.media())
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE, MediaType.IMAGE);
		assertThat(result.media())
				.extracting(ScrapedMediaDTO::position)
				.containsExactly(0, 1);
		assertThat(result.coverImagesCollected()).isEqualTo(1);
		assertThat(result.bodyImagesCollected()).isEqualTo(1);
	}

	@Test
	void fallsBackToSearchThumbnailWhenArticleMediaMissing() {
		Document document = articleDocument("""
				<div class="post_body">
				  <p>The article paragraph is intentionally long enough for the extractor fixture.</p>
				</div>
				""");

		List<ScrapedMediaDTO> media = adapter.extractMedia(document, articleCard()).media();

		assertThat(media)
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://caliber.az/media/photos/normal/thumb.webp");
	}

	private Document articleDocument(String body) {
		return Jsoup.parse("""
				<html>
				  <body>
				    <div class="post">
				      %s
				    </div>
				  </body>
				</html>
				""".formatted(body), "https://caliber.az/post/example");
	}

	private CaliberAzSearchResultCard articleCard() {
		return new CaliberAzSearchResultCard(
				"https://caliber.az/post/example",
				"Example title",
				OffsetDateTime.parse("2026-06-20T10:15:00+04:00"),
				"https://caliber.az/media/photos/normal/thumb.webp",
				3
		);
	}
}
