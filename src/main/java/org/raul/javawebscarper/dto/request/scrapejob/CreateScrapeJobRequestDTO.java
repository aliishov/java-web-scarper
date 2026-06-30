package org.raul.javawebscarper.dto.request.scrapejob;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateScrapeJobRequestDTO(
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
