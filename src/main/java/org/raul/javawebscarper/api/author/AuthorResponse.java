package org.raul.javawebscarper.api.author;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthorResponse(
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
