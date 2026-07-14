package org.raul.javawebscarper.scraper.adapter.qafqazinfoaz;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

import static org.assertj.core.api.Assertions.assertThat;

class QafqazInfoAzScraperSupportTests {

	@Test
	void supportsQafqazInfoAzSourceCode() {
		Source source = Source.builder()
				.code("QAFQAZINFO_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(QafqazInfoAzScraperSupport.supports(source)).isTrue();
	}

	@Test
	void supportsQafqazInfoAzBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://qafqazinfo.az/")
				.build();

		assertThat(QafqazInfoAzScraperSupport.supports(source)).isTrue();
	}

	@Test
	void extractsNumericExternalPostId() {
		assertThat(QafqazInfoAzScraperSupport.extractExternalPostId(
				"https://qafqazinfo.az/news/detail/kesisden-bele-rusvet-alib-7-il-hebs-verildi-513531"
		)).contains("513531");
		assertThat(QafqazInfoAzScraperSupport.extractExternalPostId(
				"https://qafqazinfo.az/news/detail/example-123"
		)).contains("123");
	}

	@Test
	void acceptsOnlyArticleDetailUrls() {
		assertThat(QafqazInfoAzScraperSupport.normalizePostUrl(
				"https://qafqazinfo.az/news/detail/kesisden-bele-rusvet-alib-7-il-hebs-verildi-513531#comments"
		)).contains("https://qafqazinfo.az/news/detail/kesisden-bele-rusvet-alib-7-il-hebs-verildi-513531");
		assertThat(QafqazInfoAzScraperSupport.normalizePostUrl("/news/detail/example-123"))
				.contains("https://qafqazinfo.az/news/detail/example-123");
		assertThat(QafqazInfoAzScraperSupport.normalizePostUrl("https://qafqazinfo.az/news/category/dunya-6")).isEmpty();
		assertThat(QafqazInfoAzScraperSupport.normalizePostUrl("https://qafqazinfo.az/news/search?keyword=test")).isEmpty();
		assertThat(QafqazInfoAzScraperSupport.normalizePostUrl("https://example.com/news/detail/example-123")).isEmpty();
		assertThat(QafqazInfoAzScraperSupport.normalizePostUrl("https://qafqazinfo.az/news/detail/example")).isEmpty();
	}

	@Test
	void filtersAdvertisingAndSocialMediaUrls() {
		assertThat(QafqazInfoAzScraperSupport.isAllowedMediaUrl("https://qafqazinfo.az/uploads/1782500137/koreya.png"))
				.isTrue();
		assertThat(QafqazInfoAzScraperSupport.isAllowedMediaUrl("https://qafqazinfo.az/banners/dynamic_banners/a.jpg"))
				.isFalse();
		assertThat(QafqazInfoAzScraperSupport.isAllowedMediaUrl("https://qafqazinfo.az/img/facebook.svg"))
				.isFalse();
		assertThat(QafqazInfoAzScraperSupport.isAllowedMediaUrl("https://telegram.org/icon.png"))
				.isFalse();
	}
}
