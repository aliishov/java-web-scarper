package org.raul.javawebscarper.api.source;

import org.raul.javawebscarper.model.enumerated.SourceType;

import java.time.OffsetDateTime;

public record SourceResponse(
		Integer id,
		String code,
		String name,
		SourceType type,
		String baseUrl,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
