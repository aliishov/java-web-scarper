package org.raul.javawebscarper.scraper.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlNormalizerTests {

	@Test
	void resolvesRelativeUrlAgainstBaseUrl() {
		String resolved = UrlNormalizer.resolve("https://BAKU.ws/news/", "../post/123?utm_source=x&id=7");

		assertThat(resolved).isEqualTo("https://baku.ws/post/123?utm_source=x&id=7");
	}

	@Test
	void removesTrackingParams() {
		String normalized = UrlNormalizer.removeTrackingParams(
				"https://baku.ws/post/123?utm_source=x&id=7&fbclid=abc&utm_campaign=test"
		);

		assertThat(normalized).isEqualTo("https://baku.ws/post/123?id=7");
	}
}
