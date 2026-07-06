package org.raul.javawebscarper.dto.scraper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ScrapedPostDTO(
		String externalPostId,
		String postUrl,
		OffsetDateTime postDate,
		ScrapedAuthorDTO author,
		String text,
		String language,
		List<ScrapedMediaDTO> media,
		Map<String, Object> metadata
) {

	public ScrapedPostDTO {
		media = media == null ? List.of() : List.copyOf(media);
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
