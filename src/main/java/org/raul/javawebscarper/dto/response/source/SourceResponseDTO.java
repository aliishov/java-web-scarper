package org.raul.javawebscarper.dto.response.source;

import org.raul.javawebscarper.model.enumerated.SourceType;

import java.time.OffsetDateTime;

public record SourceResponseDTO(
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
