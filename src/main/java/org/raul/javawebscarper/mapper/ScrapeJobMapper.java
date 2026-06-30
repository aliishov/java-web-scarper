package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.api.scrapejob.ScrapeJobRequest;
import org.raul.javawebscarper.api.scrapejob.ScrapeJobResponse;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.springframework.stereotype.Component;

@Component
public class ScrapeJobMapper {

	public ScrapeJob toEntity(ScrapeJobRequest request, Source source, Keyword keyword) {
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

	public ScrapeJobResponse toResponse(ScrapeJob scrapeJob) {
		return new ScrapeJobResponse(
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
