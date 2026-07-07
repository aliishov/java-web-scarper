package org.raul.javawebscarper.scraper.adapter.bakuws;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class BakuWsScraperSupportTests {

	@Test
	void supportsSourceByCodeOrBaseUrl() {
		Source byCode = Source.builder().code("baku_ws").baseUrl("https://example.com").build();
		Source byBaseUrl = Source.builder().code("other").baseUrl("https://baku.ws/").build();
		Source unsupported = Source.builder().code("other").baseUrl("https://example.com").build();

		assertThat(BakuWsScraperSupport.supports(byCode)).isTrue();
		assertThat(BakuWsScraperSupport.supports(byBaseUrl)).isTrue();
		assertThat(BakuWsScraperSupport.supports(unsupported)).isFalse();
	}

	@Test
	void extractsExternalPostIdFromLastUrlSegment() {
		assertThat(BakuWsScraperSupport.extractExternalPostId("https://baku.ws/diger/example-slug/"))
				.contains("example-slug");
	}

	@Test
	void normalizesPostUrlAndRemovesTrackingParameters() {
		String result = BakuWsScraperSupport.normalizePostUrl("/diger/example-slug?utm_source=x&id=7");

		assertThat(result).isEqualTo("https://baku.ws/diger/example-slug?id=7");
	}

	@Test
	void filtersBannerAndPlaceholderMediaUrls() {
		assertThat(BakuWsScraperSupport.isAllowedMediaUrl("https://baku.ws/storage/photos/a.webp")).isTrue();
		assertThat(BakuWsScraperSupport.isAllowedMediaUrl("https://baku.ws/images/banners/a.jpg")).isFalse();
		assertThat(BakuWsScraperSupport.isAllowedMediaUrl("https://baku.ws/storage/placeholder_home.jpg")).isFalse();
	}

	@Test
	void mapsMediaTypeByTagName() {
		assertThat(BakuWsScraperSupport.mediaTypeForTag("img")).isEqualTo(MediaType.IMAGE);
		assertThat(BakuWsScraperSupport.mediaTypeForTag("video")).isEqualTo(MediaType.VIDEO);
		assertThat(BakuWsScraperSupport.mediaTypeForTag("a")).isEqualTo(MediaType.UNKNOWN);
	}
}
