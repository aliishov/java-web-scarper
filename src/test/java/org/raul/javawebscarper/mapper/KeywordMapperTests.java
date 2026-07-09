package org.raul.javawebscarper.mapper;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.request.keyword.CreateKeywordRequestDTO;
import org.raul.javawebscarper.dto.response.keyword.KeywordResponseDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.enumerated.Language;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordMapperTests {

	@Test
	void createDefaultsLanguageToAz() {
		Keyword keyword = KeywordMapper.toEntity(new CreateKeywordRequestDTO("Court", null, true));

		assertThat(keyword.getWord()).isEqualTo("court");
		assertThat(keyword.getLanguage()).isEqualTo(Language.AZ);
	}

	@Test
	void responseIncludesLanguage() {
		Keyword keyword = Keyword.builder()
				.id(1)
				.word("sud")
				.language(Language.RU)
				.enabled(true)
				.build();

		KeywordResponseDTO response = KeywordMapper.toResponse(keyword);

		assertThat(response.language()).isEqualTo(Language.RU);
	}
}
