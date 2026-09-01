package org.raul.javawebscarper.dto.request.scrapejob;

import jakarta.validation.constraints.NotNull;
import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.model.enumerated.SearchRegion;

import java.time.LocalDate;

public record CreateScrapeJobRequestDTO(
		@NotNull
		Integer sourceId,

		@NotNull
		Integer keywordId,

		@NotNull
		LocalDate dateFrom,

		@NotNull
		LocalDate dateTo,

		ScrapeJobRunType runType,

		SearchRegion searchRegion
) {
}
