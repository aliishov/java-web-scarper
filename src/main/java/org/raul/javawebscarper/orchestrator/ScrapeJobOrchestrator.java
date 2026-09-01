package org.raul.javawebscarper.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.config.DailyScrapingProperties;
import org.raul.javawebscarper.config.ScrapeJobExecutionProperties;
import org.raul.javawebscarper.config.ScrapeSchedulerProperties;
import org.raul.javawebscarper.dto.response.scrapejob.DailyScrapeRunResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScheduledScrapeRunResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScrapeJobResponseDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.scheduler.PreviousDayDateRangeResolver;
import org.raul.javawebscarper.scheduler.ScrapeDateRange;
import org.raul.javawebscarper.service.KeywordService;
import org.raul.javawebscarper.service.ScrapeJobExecutionOutcome;
import org.raul.javawebscarper.service.ScrapeJobExecutionService;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.raul.javawebscarper.service.SourceLanguageSupportService;
import org.raul.javawebscarper.service.SourceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeJobOrchestrator {

	private final SourceService sourceService;
	private final KeywordService keywordService;
	private final ScrapeJobService scrapeJobService;
	private final ScrapeJobExecutionService scrapeJobExecutionService;
	private final ScrapeSchedulerProperties schedulerProperties;
	private final Clock schedulerClock;
	private final DailyScrapingProperties dailyProperties;
	private final PreviousDayDateRangeResolver previousDayDateRangeResolver;
	private final SourceLanguageSupportService sourceLanguageSupportService;
	private final ScrapeJobExecutionProperties executionProperties;
	@Qualifier("scrapeJobExecutor")
	private final Executor scrapeJobExecutor;

	public ScheduledScrapeRunResponseDTO createAndRunScheduledJobs() {
		OffsetDateTime startedAt = OffsetDateTime.now(schedulerClock);
		LocalDate dateTo = startedAt.toLocalDate();
		LocalDate dateFrom = startedAt
				.minus(schedulerProperties.getLookbackAmount(), schedulerProperties.getLookbackUnit())
				.toLocalDate();

		List<Source> sources = sourceService.findEnabledEntities();
		List<Keyword> keywords = keywordService.findEnabledEntities();

		log.info(
				"Starting scheduled scrape run: sources={}, keywords={}, dateFrom={}, dateTo={}, maxJobsPerRun={}",
				sources.size(),
				keywords.size(),
				dateFrom,
				dateTo,
				schedulerProperties.getMaxJobsPerRun()
		);

		RunCounters counters = createAndRunJobs(
				sources,
				keywords,
				dateFrom,
				dateTo,
				schedulerProperties.getMaxJobsPerRun(),
				ScrapeJobRunType.SCHEDULED,
				false
		);
		OffsetDateTime finishedAt = OffsetDateTime.now(schedulerClock);

		log.info(
				"Scheduled scrape run finished: jobsCreated={}, jobsSucceeded={}, jobsFailed={}, jobsSkipped={}, "
						+ "postsFound={}, postsSaved={}",
				counters.jobsCreated.get(),
				counters.jobsSucceeded.get(),
				counters.jobsFailed.get(),
				counters.jobsSkipped.get(),
				counters.postsFound.get(),
				counters.postsSaved.get()
		);

		return new ScheduledScrapeRunResponseDTO(
				sources.size(),
				keywords.size(),
				counters.jobsCreated.get(),
				counters.jobsSucceeded.get(),
				counters.jobsFailed.get(),
				counters.jobsSkipped.get(),
				startedAt,
				finishedAt
		);
	}

	public DailyScrapeRunResponseDTO createAndRunDailyPreviousDayJobs() {
		ZoneId zoneId = dailyProperties.getResolvedZoneId();
		OffsetDateTime startedAt = nowInZone(zoneId);
		LocalDate today = schedulerClock.instant().atZone(zoneId).toLocalDate();
		ScrapeDateRange dateRange = previousDayDateRangeResolver.resolve(today, zoneId);

		List<Source> sources = sourceService.findEnabledEntities();
		List<Keyword> keywords = keywordService.findEnabledEntities();

		log.info(
				"Starting daily previous-day scrape run: sources={}, keywords={}, dateFrom={}, dateTo={}, "
						+ "zoneId={}, maxJobsPerRun={}",
				sources.size(),
				keywords.size(),
				dateRange.dateFrom(),
				dateRange.dateTo(),
				zoneId,
				dailyProperties.getMaxJobsPerRun()
		);

		RunCounters counters = createAndRunJobs(
				sources,
				keywords,
				dateRange.fromLocalDate(),
				dateRange.toLocalDate(),
				dailyProperties.getMaxJobsPerRun(),
				ScrapeJobRunType.DAILY_PREVIOUS_DAY,
				true
		);
		OffsetDateTime finishedAt = nowInZone(zoneId);

		log.info(
				"Daily previous-day scrape run finished: jobsCreated={}, jobsSucceeded={}, jobsFailed={}, "
						+ "jobsSkipped={}, postsFound={}, postsSaved={}",
				counters.jobsCreated.get(),
				counters.jobsSucceeded.get(),
				counters.jobsFailed.get(),
				counters.jobsSkipped.get(),
				counters.postsFound.get(),
				counters.postsSaved.get()
		);

		return new DailyScrapeRunResponseDTO(
				dateRange.dateFrom(),
				dateRange.dateTo(),
				zoneId.toString(),
				sources.size(),
				keywords.size(),
				counters.jobsCreated.get(),
				counters.jobsSucceeded.get(),
				counters.jobsFailed.get(),
				counters.jobsSkipped.get(),
				counters.postsFound.get(),
				counters.postsSaved.get(),
				startedAt,
				finishedAt
		);
	}

	/** Runs one manually created job and always persists a terminal status. */
	public ScrapeJobResponseDTO runManualJob(UUID jobId) {
		ScrapeJob job = scrapeJobService.getEntityWithSourceAndKeyword(jobId);
		scrapeJobExecutionService.runPendingJob(job);
		return scrapeJobService.findById(jobId);
	}

	/** Starts a manual job without holding the administrator's HTTP request open. */
	public ScrapeJobResponseDTO startManualJob(UUID jobId) {
		scrapeJobExecutionService.startManualJob(jobId);
		return scrapeJobService.findById(jobId);
	}

	private RunCounters createAndRunJobs(
			List<Source> sources,
			List<Keyword> keywords,
			LocalDate dateFrom,
			LocalDate dateTo,
			int maxJobsPerRun,
			ScrapeJobRunType runType,
			boolean skipExistingRunType
	) {
		RunCounters counters = new RunCounters();
		List<ScrapeJobExecutionPlan> executionPlans = new ArrayList<>();
		boolean maxJobsReached = false;

		for (Source source : sources) {
			for (Keyword keyword : keywords) {
				if (counters.jobsCreated.get() >= maxJobsPerRun) {
					maxJobsReached = true;
					break;
				}
				processSourceKeywordPair(
						source,
						keyword,
						dateFrom,
						dateTo,
						runType,
						skipExistingRunType,
						counters,
						executionPlans
				);
			}
			if (maxJobsReached) {
				log.info("Max jobs per run reached: maxJobsPerRun={}", maxJobsPerRun);
				break;
			}
		}
		runCreatedJobs(executionPlans, counters);
		return counters;
	}

	private void processSourceKeywordPair(
			Source source,
			Keyword keyword,
			LocalDate dateFrom,
			LocalDate dateTo,
			ScrapeJobRunType runType,
			boolean skipExistingRunType,
			RunCounters counters,
			List<ScrapeJobExecutionPlan> executionPlans
	) {
		if (!sourceLanguageSupportService.isSupported(source, keyword)) {
			counters.jobsSkipped.incrementAndGet();
			log.info(
					"Skipping scrape job due to unsupported language: source={}, sourceLanguages={}, keyword={}, "
							+ "keywordLanguage={}, dateFrom={}, dateTo={}, runType={}",
					source.getCode(),
					source.getSupportedLanguages(),
					keyword.getWord(),
					keyword.getLanguage(),
					dateFrom,
					dateTo,
					runType
			);
			return;
		}

		if (skipExistingRunType && scrapeJobService.hasJobForRunType(source, keyword, dateFrom, dateTo, runType)) {
			counters.jobsSkipped.incrementAndGet();
			log.info(
					"Skipping duplicate scrape job for run type: source={}, keyword={}, dateFrom={}, dateTo={}, "
							+ "runType={}",
					source.getCode(),
					keyword.getWord(),
					dateFrom,
					dateTo,
					runType
			);
			return;
		}

		if (scrapeJobService.hasActiveJob(source, keyword, dateFrom, dateTo)) {
			counters.jobsSkipped.incrementAndGet();
			log.debug(
					"Skipping duplicate active scrape job: source={}, keyword={}, dateFrom={}, dateTo={}, runType={}",
					source.getCode(),
					keyword.getWord(),
					dateFrom,
					dateTo,
					runType
			);
			return;
		}

		ScrapeJob job;
		try {
			job = scrapeJobService.createPendingJob(source, keyword, dateFrom, dateTo, runType);
			counters.jobsCreated.incrementAndGet();
		} catch (DataIntegrityViolationException exception) {
			counters.jobsSkipped.incrementAndGet();
			log.info(
					"Skipping scrape job after duplicate creation conflict: source={}, keyword={}, dateFrom={}, "
							+ "dateTo={}, runType={}",
					source.getCode(),
					keyword.getWord(),
					dateFrom,
					dateTo,
					runType
			);
			log.debug("Duplicate scrape job creation conflict details", exception);
			return;
		} catch (Exception exception) {
			counters.jobsSkipped.incrementAndGet();
			log.error(
					"Failed to create scheduled scrape job: source={}, keyword={}, dateFrom={}, dateTo={}, runType={}",
					source.getCode(),
					keyword.getWord(),
					dateFrom,
					dateTo,
					runType,
					exception
			);
			return;
		}

		executionPlans.add(new ScrapeJobExecutionPlan(sourceKey(source), job));
	}

	private void runCreatedJobs(List<ScrapeJobExecutionPlan> executionPlans, RunCounters counters) {
		if (executionPlans.isEmpty()) {
			return;
		}
		Map<String, Semaphore> sourceLimits = executionPlans.stream()
				.map(ScrapeJobExecutionPlan::sourceKey)
				.distinct()
				.collect(java.util.stream.Collectors.toMap(
						sourceKey -> sourceKey,
						sourceKey -> new Semaphore(executionProperties.getPerSourceConcurrency())
				));
		List<CompletableFuture<Void>> futures = executionPlans.stream()
				.map(plan -> CompletableFuture.runAsync(
						() -> runJobWithSourceLimit(plan, sourceLimits.get(plan.sourceKey()), counters),
						scrapeJobExecutor
				))
				.toList();
		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
	}

	private void runJobWithSourceLimit(ScrapeJobExecutionPlan plan, Semaphore sourceLimit, RunCounters counters) {
		boolean acquired = false;
		try {
			sourceLimit.acquire();
			acquired = true;
			ScrapeJobExecutionOutcome outcome = scrapeJobExecutionService.runPendingJob(plan.job());
			if (outcome.success()) {
				counters.jobsSucceeded.incrementAndGet();
				counters.postsFound.addAndGet(outcome.postsFound());
				counters.postsSaved.addAndGet(outcome.postsSaved());
			} else {
				counters.jobsFailed.incrementAndGet();
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			counters.jobsFailed.incrementAndGet();
			log.error("Scheduled scrape job interrupted before execution: jobId={}", plan.job().getId(), exception);
			try {
				scrapeJobService.markFailed(plan.job().getId(), "Scrape job execution was interrupted");
			} catch (Exception statusException) {
				log.error("Failed to mark interrupted scrape job as FAILED: jobId={}", plan.job().getId(), statusException);
			}
		} catch (RuntimeException exception) {
			counters.jobsFailed.incrementAndGet();
			log.error("Scheduled scrape job failed unexpectedly: jobId={}", plan.job().getId(), exception);
			try {
				scrapeJobService.markFailed(plan.job().getId(), exception.getMessage());
			} catch (Exception statusException) {
				log.error("Failed to mark failed scrape job as FAILED: jobId={}", plan.job().getId(), statusException);
			}
		} finally {
			if (acquired) {
				sourceLimit.release();
			}
		}
	}

	private String sourceKey(Source source) {
		if (source.getId() != null) {
			return String.valueOf(source.getId());
		}
		return source.getCode() == null ? "unknown" : source.getCode();
	}

	private OffsetDateTime nowInZone(ZoneId zoneId) {
		return schedulerClock.instant().atZone(zoneId).toOffsetDateTime();
	}

	private static final class RunCounters {

		private final AtomicInteger jobsCreated = new AtomicInteger();
		private final AtomicInteger jobsSucceeded = new AtomicInteger();
		private final AtomicInteger jobsFailed = new AtomicInteger();
		private final AtomicInteger jobsSkipped = new AtomicInteger();
		private final AtomicInteger postsFound = new AtomicInteger();
		private final AtomicInteger postsSaved = new AtomicInteger();
	}

	private record ScrapeJobExecutionPlan(String sourceKey, ScrapeJob job) {
	}
}
