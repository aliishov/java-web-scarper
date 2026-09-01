package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.dto.request.scrapejob.CreateScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScrapeJobResponseDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.raul.javawebscarper.model.enumerated.SearchRegion;

public final class ScrapeJobMapper {

	private ScrapeJobMapper() {
	}

	public static ScrapeJob toEntity(CreateScrapeJobRequestDTO request, Source source, Keyword keyword) {
		return ScrapeJob.builder()
				.source(source)
				.keyword(keyword)
				.dateFrom(request.dateFrom())
				.dateTo(request.dateTo())
				.searchRegion(request.searchRegion() == null ? SearchRegion.defaultRegion() : request.searchRegion())
				.runType(request.runType() == null ? ScrapeJobRunType.MANUAL : request.runType())
				.status(ScrapeJobStatus.PENDING)
				.postsFound(0)
				.postsSaved(0)
				.build();
	}

	public static ScrapeJobResponseDTO toResponse(ScrapeJob scrapeJob) {
		SearchRegion searchRegion = resolveSearchRegion(scrapeJob);
		return new ScrapeJobResponseDTO(
				scrapeJob.getId(),
				scrapeJob.getSource().getId(),
				scrapeJob.getSource().getCode(),
				scrapeJob.getKeyword().getId(),
				scrapeJob.getKeyword().getWord(),
				scrapeJob.getDateFrom(),
				scrapeJob.getDateTo(),
				searchRegion,
				searchRegion.displayName(),
				scrapeJob.getRunType(),
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

	private static SearchRegion resolveSearchRegion(ScrapeJob scrapeJob) {
		return scrapeJob.getSearchRegion() == null ? SearchRegion.defaultRegion() : scrapeJob.getSearchRegion();
	}
}
