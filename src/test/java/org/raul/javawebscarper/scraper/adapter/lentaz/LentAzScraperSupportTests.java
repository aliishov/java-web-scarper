package org.raul.javawebscarper.scraper.adapter.lentaz;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

import static org.assertj.core.api.Assertions.assertThat;

class LentAzScraperSupportTests {

	@Test
	void supportsLentAzSourceCode() {
		Source source = Source.builder()
				.code("LENT_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(LentAzScraperSupport.supports(source)).isTrue();
	}

	@Test
	void supportsLentAzBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://lent.az/")
				.build();

		assertThat(LentAzScraperSupport.supports(source)).isTrue();
	}

	@Test
	void extractsExternalPostIdFromDataIdBeforeUrl() {
		assertThat(LentAzScraperSupport.extractExternalPostId(
				"40678059",
				"https://lent.az/xeber/hadise/usaq-piylenmeden-oldu-ata-anaya-qetl-ittihami-111"
		)).contains("40678059");
	}

	@Test
	void extractsExternalPostIdFromUrlSuffix() {
		assertThat(LentAzScraperSupport.extractExternalPostId(
				null,
				"https://lent.az/xeber/hadise/usaq-piylenmeden-oldu-ata-anaya-qetl-ittihami-40678059"
		)).contains("40678059");
	}

	@Test
	void acceptsOnlyLentArticleUrlsAndNormalizesHttps() {
		assertThat(LentAzScraperSupport.normalizePostUrl(
				"http://lent.az/xeber/hadise/usaq-piylenmeden-oldu-ata-anaya-qetl-ittihami-40678059#comments"
		)).contains("https://lent.az/xeber/hadise/usaq-piylenmeden-oldu-ata-anaya-qetl-ittihami-40678059");
		assertThat(LentAzScraperSupport.normalizePostUrl("https://lent.az/layihe")).isEmpty();
		assertThat(LentAzScraperSupport.normalizePostUrl("https://example.com/xeber/hadise/example-40678059")).isEmpty();
		assertThat(LentAzScraperSupport.normalizePostUrl("https://lent.az/xeber/hadise/example")).isEmpty();
	}

	@Test
	void filtersAdsIconsAndReactionMedia() {
		assertThat(LentAzScraperSupport.isAllowedMediaUrl("https://lent.az/storage/news/2026/june/27/big/a.webp")).isTrue();
		assertThat(LentAzScraperSupport.isAllowedMediaUrl("https://lent.az/site/assets/images/icons/desktop-logo.svg")).isFalse();
		assertThat(LentAzScraperSupport.isAllowedMediaUrl("https://lent.az/banner/web/lent-layiheler.jpg")).isFalse();
		assertThat(LentAzScraperSupport.isAllowedMediaUrl("https://newmedia.az/nativebanner/get_ads.js")).isFalse();
		assertThat(LentAzScraperSupport.isAllowedMediaUrl("https://lent.az/images/emoji/smile.svg")).isFalse();
	}
}
