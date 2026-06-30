package org.raul.javawebscarper.dto.response.post;

import org.raul.javawebscarper.model.enumerated.MediaType;

import java.time.OffsetDateTime;

public record PostMediaResponseDTO(
		Long id,
		String mediaUrl,
		MediaType mediaType,
		Integer position,
		OffsetDateTime createdAt
) {
}
