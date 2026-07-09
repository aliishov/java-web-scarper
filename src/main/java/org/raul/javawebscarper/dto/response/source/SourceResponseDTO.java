package org.raul.javawebscarper.dto.response.source;

import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.SourceType;

import java.time.OffsetDateTime;
import java.util.Set;

public record SourceResponseDTO(
		Integer id,
		String code,
		String name,
		SourceType type,
		String baseUrl,
		Set<Language> supportedLanguages,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
