package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TikTokPostUrlParserTests {

	@Test
	void parsesAbsoluteVideoUrlAndRemovesTrackingParameters() {
		TikTokPostUrl postUrl = TikTokPostUrlParser.parse(
				"https://www.tiktok.com/@court.news/video/7351234567890123456?is_from_webapp=1&sender_device=pc"
		).orElseThrow();

		assertThat(postUrl.externalPostId()).isEqualTo("7351234567890123456");
		assertThat(postUrl.username()).isEqualTo("court.news");
		assertThat(postUrl.canonicalUrl()).isEqualTo("https://www.tiktok.com/@court.news/video/7351234567890123456");
		assertThat(postUrl.shortUrl()).isFalse();
	}

	@Test
	void parsesRelativeVideoUrl() {
		TikTokPostUrl postUrl = TikTokPostUrlParser.parse("/@aznews/video/7351234567890123456").orElseThrow();

		assertThat(postUrl.canonicalUrl()).isEqualTo("https://www.tiktok.com/@aznews/video/7351234567890123456");
	}

	@Test
	void parsesShortUrlWithoutPretendingShortCodeIsExternalPostId() {
		TikTokPostUrl postUrl = TikTokPostUrlParser.parse("https://www.tiktok.com/t/ZTabc123/").orElseThrow();

		assertThat(postUrl.shortUrl()).isTrue();
		assertThat(postUrl.externalPostId()).isNull();
		assertThat(postUrl.canonicalUrl()).isEqualTo("https://www.tiktok.com/t/ZTabc123");
	}

	@Test
	void rejectsInvalidUrls() {
		assertThat(TikTokPostUrlParser.parse("https://example.com/@aznews/video/7351234567890123456")).isEmpty();
		assertThat(TikTokPostUrlParser.parse("https://www.tiktok.com/@aznews/video/not-a-number")).isEmpty();
		assertThat(TikTokPostUrlParser.parse("https://www.tiktok.com/@aznews")).isEmpty();
	}
}
