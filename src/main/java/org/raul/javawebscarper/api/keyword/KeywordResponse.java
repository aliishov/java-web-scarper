package org.raul.javawebscarper.api.keyword;

import java.time.OffsetDateTime;

public record KeywordResponse(
		Integer id,
		String word,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
