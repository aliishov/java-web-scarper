package org.raul.javawebscarper.api.scrapejob;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ScrapeJobRequest(
		@NotNull
		Integer sourceId,

		@NotNull
		Integer keywordId,

		@NotNull
		LocalDate dateFrom,

		@NotNull
		LocalDate dateTo
) {
}
