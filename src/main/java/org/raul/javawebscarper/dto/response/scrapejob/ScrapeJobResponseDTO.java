package org.raul.javawebscarper.dto.response.scrapejob;

import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.raul.javawebscarper.model.enumerated.SearchRegion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScrapeJobResponseDTO(
		UUID id,
		Integer sourceId,
		String sourceCode,
		Integer keywordId,
		String keywordWord,
		LocalDate dateFrom,
		LocalDate dateTo,
		SearchRegion searchRegion,
		String searchRegionName,
		ScrapeJobRunType runType,
		ScrapeJobStatus status,
		OffsetDateTime startedAt,
		OffsetDateTime finishedAt,
		int postsFound,
		int postsSaved,
		String errorMessage,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
