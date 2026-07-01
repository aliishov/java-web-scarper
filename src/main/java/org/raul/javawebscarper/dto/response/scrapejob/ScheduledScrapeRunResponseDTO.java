package org.raul.javawebscarper.dto.response.scrapejob;

import java.time.OffsetDateTime;

public record ScheduledScrapeRunResponseDTO(
		int sourcesCount,
		int keywordsCount,
		int jobsCreated,
		int jobsSucceeded,
		int jobsFailed,
		int jobsSkipped,
		OffsetDateTime startedAt,
		OffsetDateTime finishedAt
) {
}
