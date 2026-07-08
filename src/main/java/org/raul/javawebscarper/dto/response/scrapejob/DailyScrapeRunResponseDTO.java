package org.raul.javawebscarper.dto.response.scrapejob;

import java.time.OffsetDateTime;

public record DailyScrapeRunResponseDTO(
		OffsetDateTime dateFrom,
		OffsetDateTime dateTo,
		String zoneId,
		int sourcesCount,
		int keywordsCount,
		int jobsCreated,
		int jobsSucceeded,
		int jobsFailed,
		int jobsSkipped,
		int postsFound,
		int postsSaved,
		OffsetDateTime startedAt,
		OffsetDateTime finishedAt
) {
}
