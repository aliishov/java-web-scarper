package org.raul.javawebscarper.dto.response.keyword;

import org.raul.javawebscarper.model.enumerated.Language;

import java.time.OffsetDateTime;

public record KeywordResponseDTO(
		Integer id,
		String word,
		Language language,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
