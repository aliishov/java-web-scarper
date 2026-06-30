package org.raul.javawebscarper.api.post;

import org.raul.javawebscarper.model.enumerated.MediaType;

import java.time.OffsetDateTime;

public record PostMediaResponse(
		Long id,
		String mediaUrl,
		MediaType mediaType,
		Integer position,
		OffsetDateTime createdAt
) {
}
