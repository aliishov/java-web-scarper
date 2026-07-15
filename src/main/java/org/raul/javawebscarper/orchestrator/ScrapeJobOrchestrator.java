package org.raul.javawebscarper.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.config.DailyScrapingProperties;
import org.raul.javawebscarper.config.ScrapeSchedulerProperties;
import org.raul.javawebscarper.dto.response.scrapejob.DailyScrapeRunResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScheduledScrapeRunResponseDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.scheduler.PreviousDayDateRangeResolver;
import org.raul.javawebscarper.scheduler.ScrapeDateRange;
import org.raul.javawebscarper.scraper.ScraperResult;
import org.raul.javawebscarper.scraper.ScraperRunner;
import org.raul.javawebscarper.service.KeywordService;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.raul.javawebscarper.service.SourceLanguageSupportService;
import org.raul.javawebscarper.service.SourceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeJobOrchestrator {

	private final SourceService sourceService;
	private final KeywordService keywordService;
	private final ScrapeJobService scrapeJobService;
	private final ScraperRunner scraperRunner;
	private final ScrapeSchedulerProperties schedulerProperties;
	private final Clock schedulerClock;
	private final DailyScrapingProperties dailyProperties;
	private final PreviousDayDateRangeResolver previousDayDateRangeResolver;
	private final SourceLanguageSupportService sourceLanguageSupportService;

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
				counters.jobsCreated,
				counters.jobsSucceeded,
				counters.jobsFailed,
				counters.jobsSkipped,
				counters.postsFound,
				counters.postsSaved
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
				counters.jobsCreated,
				counters.jobsSucceeded,
				counters.jobsFailed,
				counters.jobsSkipped,
				counters.postsFound,
				counters.postsSaved
		);

		return new DailyScrapeRunResponseDTO(
				dateRange.dateFrom(),
				dateRange.dateTo(),
				zoneId.toString(),
				sources.size(),
				keywords.size(),
				counters.jobsCreated,
				counters.jobsSucceeded,
				counters.jobsFailed,
				counters.jobsSkipped,
				counters.postsFound,
				counters.postsSaved,
				startedAt,
				finishedAt
		);
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
		boolean maxJobsReached = false;

		for (Source source : sources) {
			for (Keyword keyword : keywords) {
				if (counters.jobsCreated >= maxJobsPerRun) {
					maxJobsReached = true;
					break;
				}
				processSourceKeywordPair(source, keyword, dateFrom, dateTo, runType, skipExistingRunType, counters);
			}
			if (maxJobsReached) {
				log.info("Max jobs per run reached: maxJobsPerRun={}", maxJobsPerRun);
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
			ScrapeJobRunType runType,
			boolean skipExistingRunType,
			RunCounters counters
	) {
		if (!sourceLanguageSupportService.isSupported(source, keyword)) {
			counters.jobsSkipped++;
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
			counters.jobsSkipped++;
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
			counters.jobsSkipped++;
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
			counters.jobsCreated++;
		} catch (DataIntegrityViolationException exception) {
			counters.jobsSkipped++;
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
			counters.jobsSkipped++;
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

		runJob(job, counters);
	}

	private void runJob(ScrapeJob job, RunCounters counters) {
		try {
			ScrapeJob runningJob = scrapeJobService.markRunning(job.getId());
			ScraperResult result = scraperRunner.run(runningJob);
			scrapeJobService.markSuccess(job.getId(), result.postsFound(), result.postsSaved());
			counters.postsFound += result.postsFound();
			counters.postsSaved += result.postsSaved();
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

	private OffsetDateTime nowInZone(ZoneId zoneId) {
		return schedulerClock.instant().atZone(zoneId).toOffsetDateTime();
	}

	private static final class RunCounters {

		private int jobsCreated;
		private int jobsSucceeded;
		private int jobsFailed;
		private int jobsSkipped;
		private int postsFound;
		private int postsSaved;
	}
}
