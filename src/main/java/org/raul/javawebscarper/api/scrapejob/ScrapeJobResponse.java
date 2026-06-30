package org.raul.javawebscarper.api.scrapejob;

import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScrapeJobResponse(
		UUID id,
		Integer sourceId,
		String sourceCode,
		Integer keywordId,
		String keywordWord,
		LocalDate dateFrom,
		LocalDate dateTo,
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
