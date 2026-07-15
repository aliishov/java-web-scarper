package org.raul.javawebscarper.scraper.adapter.xcom;

import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

record XPostCandidate(
		String externalPostId,
		String postUrl,
		OffsetDateTime postDate,
		ScrapedAuthorDTO author,
		String text,
		String language,
		List<ScrapedMediaDTO> media,
		Map<String, Object> metadata
) {
}
