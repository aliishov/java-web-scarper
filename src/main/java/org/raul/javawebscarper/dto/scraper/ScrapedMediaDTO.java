package org.raul.javawebscarper.dto.scraper;

import org.raul.javawebscarper.model.enumerated.MediaType;

public record ScrapedMediaDTO(
		String mediaUrl,
		MediaType mediaType,
		Integer position
) {
}
