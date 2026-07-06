package org.raul.javawebscarper.scraper.support;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

import static org.assertj.core.api.Assertions.assertThat;

class TextHashGeneratorTests {

	@Test
	void returnsStableSha256Hash() {
		String first = TextHashGenerator.sha256("hello");
		String second = TextHashGenerator.sha256("hello");

		assertThat(first).isEqualTo(second);
		assertThat(first).hasSize(64);
		assertThat(first).matches("[0-9a-f]{64}");
	}

	@Test
	void postHashIncludesSourceUrlAndText() {
		Source source = Source.builder().code("baku-ws").build();

		String first = TextHashGenerator.sha256Post(source, "https://baku.ws/post/1", "same text");
		String second = TextHashGenerator.sha256Post(source, "https://baku.ws/post/2", "same text");

		assertThat(first).isNotEqualTo(second);
	}
}
