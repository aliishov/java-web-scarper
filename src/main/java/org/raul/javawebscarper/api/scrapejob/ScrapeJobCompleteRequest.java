package org.raul.javawebscarper.api.scrapejob;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScrapeJobCompleteRequest(
		@NotNull
		@Min(0)
		Integer postsFound,

		@NotNull
		@Min(0)
		Integer postsSaved
) {
}
