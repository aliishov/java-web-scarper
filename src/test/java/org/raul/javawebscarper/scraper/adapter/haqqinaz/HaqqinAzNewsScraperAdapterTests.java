package org.raul.javawebscarper.scraper.adapter.haqqinaz;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class HaqqinAzNewsScraperAdapterTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private final HaqqinAzNewsScraperAdapter adapter = new HaqqinAzNewsScraperAdapter(
			null,
			new HaqqinAzDateParser(BAKU, Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), BAKU))
	);

	@Test
	void supportsHaqqinAzSourceCode() {
		Source source = Source.builder()
				.code("HAQQIN_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("HAQQIN_AZ");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void supportsHaqqinAzBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://haqqin.az/")
				.build();

		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void buildsEncodedFallbackSearchUrl() {
		assertThat(adapter.searchUrl("Ильхам Алиев"))
				.isEqualTo("https://haqqin.az/search/?q=%D0%98%D0%BB%D1%8C%D1%85%D0%B0%D0%BC+%D0%90%D0%BB%D0%B8%D0%B5%D0%B2");
	}

	@Test
	void extractsExternalIdFromNewsArchiveUrl() {
		assertThat(HaqqinAzScraperSupport.extractExternalPostId("https://haqqin.az/newsarchive/387035"))
				.contains("387035");
		assertThat(HaqqinAzScraperSupport.extractExternalPostId("/newsarchive/387035"))
				.contains("387035");
	}

	@Test
	void acceptsNewsArticleUrlPatternsOnly() {
		assertThat(HaqqinAzScraperSupport.normalizeArticleUrl("https://haqqin.az/newsarchive/387035"))
				.contains("https://haqqin.az/newsarchive/387035");
		assertThat(HaqqinAzScraperSupport.normalizeArticleUrl("https://haqqin.az/news/387035"))
				.contains("https://haqqin.az/news/387035");
		assertThat(HaqqinAzScraperSupport.normalizeArticleUrl("https://haqqin.az/search/?q=test")).isEmpty();
		assertThat(HaqqinAzScraperSupport.normalizeArticleUrl("https://t.me/example")).isEmpty();
		assertThat(HaqqinAzScraperSupport.normalizeArticleUrl("https://youtube.com/watch?v=1")).isEmpty();
	}

	@Test
	void extractsSearchCardWithTimeOnlyDateAndThumbnail() {
		Element card = Jsoup.parse("""
				<a href="https://haqqin.az/newsarchive/387035" class="news-list__item news-list__item--narrow news-item">
				  <div class="news-item__image">
				    <img src="https://i.haqqin.az/991980_res_0_e9c6cef5503a1c8d.webp" alt="Title">
				  </div>
				  <div class="news-item__text">
				    <span class="news-item__title">Тегеран о проходе через Ормуз</span>
				    <div class="news-item__pubinfo news-item-pubinfo">
				      <dev class="news-item-pubinfo__date">15:31</dev>
				      <dev class="news-item-pubinfo__views">328</dev>
				    </div>
				  </div>
				</a>
				""", "https://haqqin.az/")
				.selectFirst("a.news-item");

		HaqqinAzNewsScraperAdapter.SearchCardParseResult parsed = adapter.parseResultCard(card, 2);

		assertThat(parsed.card()).isNotNull();
		assertThat(parsed.card().postUrl()).isEqualTo("https://haqqin.az/newsarchive/387035");
		assertThat(parsed.card().title()).isEqualTo("Тегеран о проходе через Ормуз");
		assertThat(parsed.card().searchDate()).isEqualTo(OffsetDateTime.parse("2026-07-13T15:31:00+04:00"));
		assertThat(parsed.card().thumbnailUrl()).isEqualTo("https://i.haqqin.az/991980_res_0_e9c6cef5503a1c8d.webp");
		assertThat(parsed.card().searchBatch()).isEqualTo(2);
	}

	@Test
	void rejectsExternalSearchCardUrl() {
		Element card = Jsoup.parse("""
				<a href="https://example.com/newsarchive/387035" class="news-item">
				  <span class="news-item__title">External</span>
				  <div class="news-item-pubinfo__date">15:31</div>
				</a>
				""", "https://haqqin.az/")
				.selectFirst("a.news-item");

		HaqqinAzNewsScraperAdapter.SearchCardParseResult parsed = adapter.parseResultCard(card, 1);

		assertThat(parsed.card()).isNull();
		assertThat(parsed.skipReason())
				.isEqualTo(HaqqinAzNewsScraperAdapter.SearchCardSkipReason.EXTERNAL_OR_INVALID_URL);
	}

	@Test
	void readsNextPageValueFromLoadMoreButton() {
		Document document = Jsoup.parse("""
				<section class="load-more">
				  <div class="load-more__button" data-next-page="4">Ранее</div>
				</section>
				""", "https://haqqin.az/search/?q=test");

		assertThat(adapter.nextPageValue(document)).contains("4");
	}

	@Test
	void ignoresDisabledLoadMoreButton() {
		Document document = Jsoup.parse("""
				<section class="load-more">
				  <div class="load-more__button disabled" data-next-page="4">Ранее</div>
				</section>
				""", "https://haqqin.az/search/?q=test");

		assertThat(adapter.nextPageValue(document)).isEmpty();
	}
}
