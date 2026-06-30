package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.dto.request.keyword.CreateKeywordRequestDTO;
import org.raul.javawebscarper.dto.request.keyword.UpdateKeywordRequestDTO;
import org.raul.javawebscarper.dto.response.keyword.KeywordResponseDTO;
import org.raul.javawebscarper.model.Keyword;

public final class KeywordMapper {

	private KeywordMapper() {
	}

	public static Keyword toEntity(CreateKeywordRequestDTO request) {
		return Keyword.builder()
				.word(normalize(request.word()))
				.enabled(request.enabled() == null || request.enabled())
				.build();
	}

	public static void updateEntity(Keyword keyword, UpdateKeywordRequestDTO request) {
		keyword.setWord(normalize(request.word()));
		keyword.setEnabled(request.enabled() == null || request.enabled());
	}

	public static KeywordResponseDTO toResponse(Keyword keyword) {
		return new KeywordResponseDTO(
				keyword.getId(),
				keyword.getWord(),
				keyword.isEnabled(),
				keyword.getCreatedAt(),
				keyword.getUpdatedAt()
		);
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase();
	}
}
