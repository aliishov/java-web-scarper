package org.raul.javawebscarper.scraper.adapter.facebook;

import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record FacebookPostCandidate(
		String externalPostId,
		String postUrl,
		FacebookPostType postType,
		OffsetDateTime postDate,
		ScrapedAuthorDTO author,
		String text,
		String language,
		List<ScrapedMediaDTO> media,
		Map<String, Object> metadata
) {

	public FacebookPostCandidate {
		media = media == null ? List.of() : List.copyOf(media);
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
