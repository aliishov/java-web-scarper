package org.raul.javawebscarper.scraper.engine;

import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;

import java.time.OffsetDateTime;
import java.util.Map;

public record ScraperExecutionContext(
		ScrapeJob job,
		Source source,
		Keyword keyword,
		OffsetDateTime dateFrom,
		OffsetDateTime dateTo,
		int maxPages,
		int maxPosts,
		Map<String, Object> metadata
) {

	public ScraperExecutionContext {
		if (maxPages < 1) {
			throw new IllegalArgumentException("maxPages must be positive");
		}
		if (maxPosts < 1) {
			throw new IllegalArgumentException("maxPosts must be positive");
		}
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
