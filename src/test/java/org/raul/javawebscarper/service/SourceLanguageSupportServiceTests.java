package org.raul.javawebscarper.service;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class SourceLanguageSupportServiceTests {

	private final SourceLanguageSupportService service = new SourceLanguageSupportService();

	@Test
	void bakuWsSupportsAzKeyword() {
		Source source = Source.builder()
				.code("baku_ws")
				.supportedLanguages(EnumSet.of(Language.AZ))
				.build();
		Keyword keyword = Keyword.builder()
				.word("court")
				.language(Language.AZ)
				.build();

		assertThat(service.isSupported(source, keyword)).isTrue();
	}

	@Test
	void bakuWsDoesNotSupportRuKeyword() {
		Source source = Source.builder()
				.code("baku_ws")
				.supportedLanguages(EnumSet.of(Language.AZ))
				.build();
		Keyword keyword = Keyword.builder()
				.word("sud")
				.language(Language.RU)
				.build();

		assertThat(service.isSupported(source, keyword)).isFalse();
	}

	@Test
	void mediaAzSupportsRuKeyword() {
		Source source = Source.builder()
				.code("media_az")
				.supportedLanguages(EnumSet.of(Language.RU))
				.build();
		Keyword keyword = Keyword.builder()
				.word("sud")
				.language(Language.RU)
				.build();

		assertThat(service.isSupported(source, keyword)).isTrue();
	}

	@Test
	void mediaAzDoesNotSupportAzKeyword() {
		Source source = Source.builder()
				.code("media_az")
				.supportedLanguages(EnumSet.of(Language.RU))
				.build();
		Keyword keyword = Keyword.builder()
				.word("court")
				.language(Language.AZ)
				.build();

		assertThat(service.isSupported(source, keyword)).isFalse();
	}
}
