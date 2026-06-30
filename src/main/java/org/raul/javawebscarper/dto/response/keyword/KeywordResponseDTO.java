package org.raul.javawebscarper.dto.response.keyword;

import java.time.OffsetDateTime;

public record KeywordResponseDTO(
		Integer id,
		String word,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
