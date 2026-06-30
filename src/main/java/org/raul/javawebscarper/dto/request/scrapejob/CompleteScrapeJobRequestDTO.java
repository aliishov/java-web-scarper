package org.raul.javawebscarper.dto.request.scrapejob;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CompleteScrapeJobRequestDTO(
		@NotNull
		@Min(0)
		Integer postsFound,

		@NotNull
		@Min(0)
		Integer postsSaved
) {
}
