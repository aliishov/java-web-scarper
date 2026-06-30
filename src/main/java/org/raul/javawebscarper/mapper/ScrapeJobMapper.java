package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.dto.request.scrapejob.CreateScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScrapeJobResponseDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;

public final class ScrapeJobMapper {

	private ScrapeJobMapper() {
	}

	public static ScrapeJob toEntity(CreateScrapeJobRequestDTO request, Source source, Keyword keyword) {
		return ScrapeJob.builder()
				.source(source)
				.keyword(keyword)
				.dateFrom(request.dateFrom())
				.dateTo(request.dateTo())
				.status(ScrapeJobStatus.PENDING)
				.postsFound(0)
				.postsSaved(0)
				.build();
	}

	public static ScrapeJobResponseDTO toResponse(ScrapeJob scrapeJob) {
		return new ScrapeJobResponseDTO(
				scrapeJob.getId(),
				scrapeJob.getSource().getId(),
				scrapeJob.getSource().getCode(),
				scrapeJob.getKeyword().getId(),
				scrapeJob.getKeyword().getWord(),
				scrapeJob.getDateFrom(),
				scrapeJob.getDateTo(),
				scrapeJob.getStatus(),
				scrapeJob.getStartedAt(),
				scrapeJob.getFinishedAt(),
				scrapeJob.getPostsFound(),
				scrapeJob.getPostsSaved(),
				scrapeJob.getErrorMessage(),
				scrapeJob.getCreatedAt(),
				scrapeJob.getUpdatedAt()
		);
	}
}
