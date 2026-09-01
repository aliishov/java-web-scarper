package org.raul.javawebscarper.scraper.engine;

import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.SearchRegion;

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
		SearchRegion searchRegion,
		Map<String, Object> metadata
) {

	public ScraperExecutionContext {
		if (maxPages < 1) {
			throw new IllegalArgumentException("maxPages must be positive");
		}
		if (maxPosts < 1) {
			throw new IllegalArgumentException("maxPosts must be positive");
		}
		searchRegion = searchRegion == null ? SearchRegion.defaultRegion() : searchRegion;
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}

	public ScraperExecutionContext(
			ScrapeJob job,
			Source source,
			Keyword keyword,
			OffsetDateTime dateFrom,
			OffsetDateTime dateTo,
			int maxPages,
			int maxPosts,
			Map<String, Object> metadata
	) {
		this(job, source, keyword, dateFrom, dateTo, maxPages, maxPosts, SearchRegion.defaultRegion(), metadata);
	}
}
