package org.raul.javawebscarper.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.config.ScrapeSchedulerProperties;
import org.raul.javawebscarper.dto.response.scrapejob.ScheduledScrapeRunResponseDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.ScraperResult;
import org.raul.javawebscarper.scraper.ScraperRunner;
import org.raul.javawebscarper.service.KeywordService;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.raul.javawebscarper.service.SourceService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeJobOrchestrator {

	private final SourceService sourceService;
	private final KeywordService keywordService;
	private final ScrapeJobService scrapeJobService;
	private final ScraperRunner scraperRunner;
	private final ScrapeSchedulerProperties properties;
	private final Clock schedulerClock;

	public ScheduledScrapeRunResponseDTO createAndRunScheduledJobs() {
		OffsetDateTime startedAt = OffsetDateTime.now(schedulerClock);
		LocalDate dateTo = startedAt.toLocalDate();
		LocalDate dateFrom = startedAt
				.minus(properties.getLookbackAmount(), properties.getLookbackUnit())
				.toLocalDate();

		List<Source> sources = sourceService.findEnabledEntities();
		List<Keyword> keywords = keywordService.findEnabledEntities();

		log.info(
				"Starting scheduled scrape run: sources={}, keywords={}, dateFrom={}, dateTo={}, maxJobsPerRun={}",
				sources.size(),
				keywords.size(),
				dateFrom,
				dateTo,
				properties.getMaxJobsPerRun()
		);

		RunCounters counters = createAndRunJobs(sources, keywords, dateFrom, dateTo);
		OffsetDateTime finishedAt = OffsetDateTime.now(schedulerClock);

		log.info(
				"Scheduled scrape run finished: jobsCreated={}, jobsSucceeded={}, jobsFailed={}, jobsSkipped={}",
				counters.jobsCreated,
				counters.jobsSucceeded,
				counters.jobsFailed,
				counters.jobsSkipped
		);

		return new ScheduledScrapeRunResponseDTO(
				sources.size(),
				keywords.size(),
				counters.jobsCreated,
				counters.jobsSucceeded,
				counters.jobsFailed,
				counters.jobsSkipped,
				startedAt,
				finishedAt
		);
	}

	private RunCounters createAndRunJobs(List<Source> sources, List<Keyword> keywords, LocalDate dateFrom, LocalDate dateTo) {
		RunCounters counters = new RunCounters();
		boolean maxJobsReached = false;

		for (Source source : sources) {
			for (Keyword keyword : keywords) {
				if (counters.jobsCreated >= properties.getMaxJobsPerRun()) {
					maxJobsReached = true;
					break;
				}
				processSourceKeywordPair(source, keyword, dateFrom, dateTo, counters);
			}
			if (maxJobsReached) {
				log.info("Max jobs per run reached: maxJobsPerRun={}", properties.getMaxJobsPerRun());
				break;
			}
		}
		return counters;
	}

	private void processSourceKeywordPair(
			Source source,
			Keyword keyword,
			LocalDate dateFrom,
			LocalDate dateTo,
			RunCounters counters
	) {
		if (scrapeJobService.hasActiveJob(source, keyword, dateFrom, dateTo)) {
			counters.jobsSkipped++;
			log.debug(
					"Skipping duplicate active scrape job: source={}, keyword={}, dateFrom={}, dateTo={}",
					source.getCode(),
					keyword.getWord(),
					dateFrom,
					dateTo
			);
			return;
		}

		ScrapeJob job;
		try {
			job = scrapeJobService.createPendingJob(source, keyword, dateFrom, dateTo);
			counters.jobsCreated++;
		} catch (Exception exception) {
			counters.jobsSkipped++;
			log.error(
					"Failed to create scheduled scrape job: source={}, keyword={}, dateFrom={}, dateTo={}",
					source.getCode(),
					keyword.getWord(),
					dateFrom,
					dateTo,
					exception
			);
			return;
		}

		runJob(job, counters);
	}

	private void runJob(ScrapeJob job, RunCounters counters) {
		try {
			ScrapeJob runningJob = scrapeJobService.markRunning(job.getId());
			ScraperResult result = scraperRunner.run(runningJob);
			scrapeJobService.markSuccess(job.getId(), result.postsFound(), result.postsSaved());
			counters.jobsSucceeded++;
		} catch (Exception exception) {
			counters.jobsFailed++;
			log.error("Scheduled scrape job failed: jobId={}", job.getId(), exception);
			markJobFailed(job, exception);
		}
	}

	private void markJobFailed(ScrapeJob job, Exception exception) {
		try {
			scrapeJobService.markFailed(job.getId(), exception.getMessage());
		} catch (Exception statusException) {
			log.error("Failed to mark scrape job as FAILED: jobId={}", job.getId(), statusException);
		}
	}

	private static final class RunCounters {

		private int jobsCreated;
		private int jobsSucceeded;
		private int jobsFailed;
		private int jobsSkipped;
	}
}
