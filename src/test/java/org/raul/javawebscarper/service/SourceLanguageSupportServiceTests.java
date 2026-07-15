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
	void mediaAzUppercaseCodeSupportsRuKeyword() {
		Source source = Source.builder()
				.code("MEDIA_AZ")
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

	@Test
	void oneNewsAzSupportsAzKeyword() {
		Source source = Source.builder()
				.code("ONE_NEWS_AZ")
				.supportedLanguages(EnumSet.of(Language.AZ))
				.build();
		Keyword keyword = Keyword.builder()
				.word("mehkeme")
				.language(Language.AZ)
				.build();

		assertThat(service.isSupported(source, keyword)).isTrue();
	}

	@Test
	void oneNewsAzDoesNotSupportRuKeyword() {
		Source source = Source.builder()
				.code("ONE_NEWS_AZ")
				.supportedLanguages(EnumSet.of(Language.AZ))
				.build();
		Keyword keyword = Keyword.builder()
				.word("sud")
				.language(Language.RU)
				.build();

		assertThat(service.isSupported(source, keyword)).isFalse();
	}

	@Test
	void haqqinAzSupportsRuKeyword() {
		Source source = Source.builder()
				.code("HAQQIN_AZ")
				.supportedLanguages(EnumSet.of(Language.RU))
				.build();
		Keyword keyword = Keyword.builder()
				.word("sud")
				.language(Language.RU)
				.build();

		assertThat(service.isSupported(source, keyword)).isTrue();
	}

	@Test
	void haqqinAzDoesNotSupportAzKeyword() {
		Source source = Source.builder()
				.code("HAQQIN_AZ")
				.supportedLanguages(EnumSet.of(Language.RU))
				.build();
		Keyword keyword = Keyword.builder()
				.word("mehkeme")
				.language(Language.AZ)
				.build();

		assertThat(service.isSupported(source, keyword)).isFalse();
	}

	@Test
	void caliberAzSupportsRuKeyword() {
		Source source = Source.builder()
				.code("CALIBER_AZ")
				.supportedLanguages(EnumSet.of(Language.RU))
				.build();
		Keyword keyword = Keyword.builder()
				.word("sud")
				.language(Language.RU)
				.build();

		assertThat(service.isSupported(source, keyword)).isTrue();
	}

	@Test
	void caliberAzDoesNotSupportAzKeyword() {
		Source source = Source.builder()
				.code("CALIBER_AZ")
				.supportedLanguages(EnumSet.of(Language.RU))
				.build();
		Keyword keyword = Keyword.builder()
				.word("mehkeme")
				.language(Language.AZ)
				.build();

		assertThat(service.isSupported(source, keyword)).isFalse();
	}

	@Test
	void qafqazInfoAzSupportsAzKeyword() {
		Source source = Source.builder()
				.code("QAFQAZINFO_AZ")
				.supportedLanguages(EnumSet.of(Language.AZ))
				.build();
		Keyword keyword = Keyword.builder()
				.word("mehkeme")
				.language(Language.AZ)
				.build();

		assertThat(service.isSupported(source, keyword)).isTrue();
	}

	@Test
	void qafqazInfoAzDoesNotSupportRuKeyword() {
		Source source = Source.builder()
				.code("QAFQAZINFO_AZ")
				.supportedLanguages(EnumSet.of(Language.AZ))
				.build();
		Keyword keyword = Keyword.builder()
				.word("sud")
				.language(Language.RU)
				.build();

		assertThat(service.isSupported(source, keyword)).isFalse();
	}

	@Test
	void lentAzSupportsAzKeyword() {
		Source source = Source.builder()
				.code("LENT_AZ")
				.supportedLanguages(EnumSet.of(Language.AZ))
				.build();
		Keyword keyword = Keyword.builder()
				.word("mehkeme")
				.language(Language.AZ)
				.build();

		assertThat(service.isSupported(source, keyword)).isTrue();
	}

	@Test
	void lentAzDoesNotSupportRuKeyword() {
		Source source = Source.builder()
				.code("LENT_AZ")
				.supportedLanguages(EnumSet.of(Language.AZ))
				.build();
		Keyword keyword = Keyword.builder()
				.word("sud")
				.language(Language.RU)
				.build();

		assertThat(service.isSupported(source, keyword)).isFalse();
	}

	@Test
	void xComSupportsAllConfiguredKeywordLanguages() {
		Source source = Source.builder()
				.code("X_COM")
				.supportedLanguages(EnumSet.allOf(Language.class))
				.build();

		assertThat(service.isSupported(source, Keyword.builder().word("mehkeme").language(Language.AZ).build())).isTrue();
		assertThat(service.isSupported(source, Keyword.builder().word("sud").language(Language.RU).build())).isTrue();
		assertThat(service.isSupported(source, Keyword.builder().word("court").language(Language.EN).build())).isTrue();
		assertThat(service.isSupported(source, Keyword.builder().word("mahkeme").language(Language.TR).build())).isTrue();
	}
}
