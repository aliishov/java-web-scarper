package org.raul.javawebscarper.mapper;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.request.source.CreateSourceRequestDTO;
import org.raul.javawebscarper.dto.response.source.SourceResponseDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.SourceType;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMapperTests {

	@Test
	void createDefaultsSupportedLanguagesToAz() {
		Source source = SourceMapper.toEntity(new CreateSourceRequestDTO(
				"baku_ws",
				"baku.ws",
				SourceType.NEWS,
				"https://baku.ws",
				null,
				true
		));

		assertThat(source.getSupportedLanguages()).containsExactly(Language.AZ);
	}

	@Test
	void sourceCanSupportMultipleLanguages() {
		Source source = SourceMapper.toEntity(new CreateSourceRequestDTO(
				"multi_news",
				"Multi News",
				SourceType.NEWS,
				"https://example.com",
				EnumSet.of(Language.AZ, Language.RU),
				true
		));

		assertThat(source.getSupportedLanguages()).containsExactlyInAnyOrder(Language.AZ, Language.RU);
	}

	@Test
	void responseIncludesSupportedLanguages() {
		Source source = Source.builder()
				.id(1)
				.code("media_az")
				.name("Media.az")
				.type(SourceType.NEWS)
				.baseUrl("https://media.az")
				.supportedLanguages(EnumSet.of(Language.RU))
				.enabled(true)
				.build();

		SourceResponseDTO response = SourceMapper.toResponse(source);

		assertThat(response.supportedLanguages()).containsExactly(Language.RU);
	}
}
