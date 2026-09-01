package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.config.ScrapeJobExecutionProperties;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.scraper.ScraperResult;
import org.raul.javawebscarper.scraper.ScraperRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeJobExecutionService {

	private final ScrapeJobService scrapeJobService;
	private final ScraperRunner scraperRunner;
	private final ScrapeJobExecutionProperties properties;
	@Qualifier("scrapeJobExecutor")
	private final Executor scrapeJobExecutor;

	public ScrapeJobExecutionOutcome runPendingJob(ScrapeJob job) {
		if (!properties.isEnabled()) {
			log.info("Scrape job execution is disabled; leaving job pending: jobId={}", job.getId());
			return ScrapeJobExecutionOutcome.failed();
		}
		try {
			ScrapeJob runningJob = scrapeJobService.markRunning(job.getId());
			return runAlreadyRunningJob(runningJob);
		} catch (Exception exception) {
			log.error("Scrape job failed: jobId={}", job.getId(), exception);
			markJobFailed(job.getId(), exception);
			return ScrapeJobExecutionOutcome.failed();
		}
	}

	public void startManualJob(UUID jobId) {
		if (!properties.isEnabled()) {
			throw new BadRequestException("Scrape job execution is disabled");
		}
		scrapeJobService.markRunning(jobId);
		CompletableFuture.runAsync(() -> runManualJobInBackground(jobId), scrapeJobExecutor);
	}

	private void runManualJobInBackground(UUID jobId) {
		try {
			ScrapeJob job = scrapeJobService.getEntityWithSourceAndKeyword(jobId);
			runAlreadyRunningJob(job);
		} catch (Exception exception) {
			log.error("Manual scrape job failed: jobId={}", jobId, exception);
			markJobFailed(jobId, exception);
		}
	}

	private ScrapeJobExecutionOutcome runAlreadyRunningJob(ScrapeJob job) {
		try {
			ScraperResult result = scraperRunner.run(job);
			scrapeJobService.markSuccess(job.getId(), result.postsFound(), result.postsSaved());
			return ScrapeJobExecutionOutcome.success(result.postsFound(), result.postsSaved());
		} catch (Exception exception) {
			log.error("Scrape job execution failed: jobId={}", job.getId(), exception);
			markJobFailed(job.getId(), exception);
			return ScrapeJobExecutionOutcome.failed();
		}
	}

	private void markJobFailed(UUID jobId, Exception exception) {
		try {
			scrapeJobService.markFailed(jobId, exception.getMessage());
		} catch (Exception statusException) {
			log.error("Could not mark scrape job as FAILED: jobId={}", jobId, statusException);
		}
	}
}
