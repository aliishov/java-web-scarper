package org.raul.javawebscarper.dto.response.author;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthorResponseDTO(
		UUID id,
		Integer sourceId,
		String sourceCode,
		String username,
		String externalId,
		String profileUrl,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
