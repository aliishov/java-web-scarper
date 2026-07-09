package org.raul.javawebscarper.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.raul.javawebscarper.config.DailyScrapingProperties;
import org.raul.javawebscarper.config.ScrapeSchedulerProperties;
import org.raul.javawebscarper.dto.response.scrapejob.DailyScrapeRunResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScheduledScrapeRunResponseDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.raul.javawebscarper.scheduler.PreviousDayDateRangeResolver;
import org.raul.javawebscarper.scraper.ScraperResult;
import org.raul.javawebscarper.scraper.ScraperRunner;
import org.raul.javawebscarper.service.KeywordService;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.raul.javawebscarper.service.SourceService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapeJobOrchestratorTests {

	private static final Clock FIXED_CLOCK = Clock.fixed(
			Instant.parse("2026-07-01T08:30:00Z"),
			ZoneOffset.UTC
	);
	private static final LocalDate DATE_FROM = LocalDate.of(2026, 6, 30);
	private static final LocalDate DATE_TO = LocalDate.of(2026, 7, 1);
	private static final LocalDate DAILY_DATE_TO = LocalDate.of(2026, 6, 30);

	@Mock
	private SourceService sourceService;

	@Mock
	private KeywordService keywordService;

	@Mock
	private ScrapeJobService scrapeJobService;

	@Mock
	private ScraperRunner scraperRunner;

	private ScrapeSchedulerProperties properties;
	private DailyScrapingProperties dailyProperties;
	private ScrapeJobOrchestrator orchestrator;
	private Source source;
	private Keyword keyword;
	private ScrapeJob job;

	@BeforeEach
	void setUp() {
		properties = new ScrapeSchedulerProperties();
		properties.setLookbackAmount(1);
		properties.setLookbackUnit(ChronoUnit.DAYS);
		properties.setMaxJobsPerRun(100);
		dailyProperties = new DailyScrapingProperties();
		dailyProperties.setZoneId("Asia/Baku");
		dailyProperties.setMaxJobsPerRun(100);

		orchestrator = new ScrapeJobOrchestrator(
				sourceService,
				keywordService,
				scrapeJobService,
				scraperRunner,
				properties,
				FIXED_CLOCK,
				dailyProperties,
				new PreviousDayDateRangeResolver()
		);

		source = Source.builder()
				.id(1)
				.code("baku-ws")
				.name("baku.ws")
				.enabled(true)
				.build();
		keyword = Keyword.builder()
				.id(1)
				.word("economy")
				.enabled(true)
				.build();
		job = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.dateFrom(DATE_FROM)
				.dateTo(DATE_TO)
				.status(ScrapeJobStatus.PENDING)
				.build();
	}

	@Test
	void createsAndRunsJobsForEnabledSourcesAndKeywords() {
		when(sourceService.findEnabledEntities()).thenReturn(List.of(source));
		when(keywordService.findEnabledEntities()).thenReturn(List.of(keyword));
		when(scrapeJobService.hasActiveJob(source, keyword, DATE_FROM, DATE_TO)).thenReturn(false);
		when(scrapeJobService.createPendingJob(
				source,
				keyword,
				DATE_FROM,
				DATE_TO,
				ScrapeJobRunType.SCHEDULED
		)).thenReturn(job);
		when(scrapeJobService.markRunning(job.getId())).thenReturn(job);
		when(scraperRunner.run(job)).thenReturn(ScraperResult.empty());

		ScheduledScrapeRunResponseDTO response = orchestrator.createAndRunScheduledJobs();

		assertThat(response.sourcesCount()).isEqualTo(1);
		assertThat(response.keywordsCount()).isEqualTo(1);
		assertThat(response.jobsCreated()).isEqualTo(1);
		assertThat(response.jobsSucceeded()).isEqualTo(1);
		assertThat(response.jobsFailed()).isZero();
		assertThat(response.jobsSkipped()).isZero();
		verify(scrapeJobService).markSuccess(job.getId(), 0, 0);
		verify(scrapeJobService, never()).hasJobForRunType(
				source,
				keyword,
				DATE_FROM,
				DATE_TO,
				ScrapeJobRunType.SCHEDULED
		);
	}

	@Test
	void skipsDuplicateActiveJobs() {
		when(sourceService.findEnabledEntities()).thenReturn(List.of(source));
		when(keywordService.findEnabledEntities()).thenReturn(List.of(keyword));
		when(scrapeJobService.hasActiveJob(source, keyword, DATE_FROM, DATE_TO)).thenReturn(true);

		ScheduledScrapeRunResponseDTO response = orchestrator.createAndRunScheduledJobs();

		assertThat(response.jobsCreated()).isZero();
		assertThat(response.jobsSucceeded()).isZero();
		assertThat(response.jobsFailed()).isZero();
		assertThat(response.jobsSkipped()).isEqualTo(1);
		verify(scrapeJobService, never()).createPendingJob(
				source,
				keyword,
				DATE_FROM,
				DATE_TO,
				ScrapeJobRunType.SCHEDULED
		);
	}

	@Test
	void failedScraperMarksJobAsFailed() {
		RuntimeException scraperFailure = new RuntimeException("scraper unavailable");
		when(sourceService.findEnabledEntities()).thenReturn(List.of(source));
		when(keywordService.findEnabledEntities()).thenReturn(List.of(keyword));
		when(scrapeJobService.hasActiveJob(source, keyword, DATE_FROM, DATE_TO)).thenReturn(false);
		when(scrapeJobService.createPendingJob(
				source,
				keyword,
				DATE_FROM,
				DATE_TO,
				ScrapeJobRunType.SCHEDULED
		)).thenReturn(job);
		when(scrapeJobService.markRunning(job.getId())).thenReturn(job);
		when(scraperRunner.run(job)).thenThrow(scraperFailure);

		ScheduledScrapeRunResponseDTO response = orchestrator.createAndRunScheduledJobs();

		assertThat(response.jobsCreated()).isEqualTo(1);
		assertThat(response.jobsSucceeded()).isZero();
		assertThat(response.jobsFailed()).isEqualTo(1);
		assertThat(response.jobsSkipped()).isZero();
		verify(scrapeJobService).markFailed(job.getId(), "scraper unavailable");
	}

	@Test
	void respectsMaxJobsPerRun() {
		Keyword secondKeyword = Keyword.builder()
				.id(2)
				.word("finance")
				.enabled(true)
				.build();
		properties.setMaxJobsPerRun(1);
		when(sourceService.findEnabledEntities()).thenReturn(List.of(source));
		when(keywordService.findEnabledEntities()).thenReturn(List.of(keyword, secondKeyword));
		when(scrapeJobService.hasActiveJob(source, keyword, DATE_FROM, DATE_TO)).thenReturn(false);
		when(scrapeJobService.createPendingJob(
				source,
				keyword,
				DATE_FROM,
				DATE_TO,
				ScrapeJobRunType.SCHEDULED
		)).thenReturn(job);
		when(scrapeJobService.markRunning(job.getId())).thenReturn(job);
		when(scraperRunner.run(job)).thenReturn(ScraperResult.empty());

		ScheduledScrapeRunResponseDTO response = orchestrator.createAndRunScheduledJobs();

		assertThat(response.jobsCreated()).isEqualTo(1);
		verify(scrapeJobService, never()).hasActiveJob(source, secondKeyword, DATE_FROM, DATE_TO);
		verify(scraperRunner).run(job);
		verifyNoMoreInteractions(scraperRunner);
	}

	@Test
	void createsDailyPreviousDayJobs() {
		ScrapeJob dailyJob = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.dateFrom(DATE_FROM)
				.dateTo(DAILY_DATE_TO)
				.runType(ScrapeJobRunType.DAILY_PREVIOUS_DAY)
				.status(ScrapeJobStatus.PENDING)
				.build();
		ScraperResult scraperResult = new ScraperResult(
				3,
				2,
				List.of(UUID.randomUUID(), UUID.randomUUID())
		);
		when(sourceService.findEnabledEntities()).thenReturn(List.of(source));
		when(keywordService.findEnabledEntities()).thenReturn(List.of(keyword));
		when(scrapeJobService.hasJobForRunType(
				source,
				keyword,
				DATE_FROM,
				DAILY_DATE_TO,
				ScrapeJobRunType.DAILY_PREVIOUS_DAY
		)).thenReturn(false);
		when(scrapeJobService.hasActiveJob(source, keyword, DATE_FROM, DAILY_DATE_TO)).thenReturn(false);
		when(scrapeJobService.createPendingJob(
				source,
				keyword,
				DATE_FROM,
				DAILY_DATE_TO,
				ScrapeJobRunType.DAILY_PREVIOUS_DAY
		)).thenReturn(dailyJob);
		when(scrapeJobService.markRunning(dailyJob.getId())).thenReturn(dailyJob);
		when(scraperRunner.run(dailyJob)).thenReturn(scraperResult);

		DailyScrapeRunResponseDTO response = orchestrator.createAndRunDailyPreviousDayJobs();

		assertThat(response.dateFrom()).isEqualTo(OffsetDateTime.parse("2026-06-30T00:00+04:00"));
		assertThat(response.dateTo()).isEqualTo(OffsetDateTime.parse("2026-06-30T23:59:59.999999999+04:00"));
		assertThat(response.zoneId()).isEqualTo("Asia/Baku");
		assertThat(response.jobsCreated()).isEqualTo(1);
		assertThat(response.jobsSucceeded()).isEqualTo(1);
		assertThat(response.jobsFailed()).isZero();
		assertThat(response.postsFound()).isEqualTo(3);
		assertThat(response.postsSaved()).isEqualTo(2);
		verify(scrapeJobService).markSuccess(dailyJob.getId(), 3, 2);
	}

	@Test
	void secondDailyPreviousDayRunSkipsExistingJobs() {
		ScrapeJob dailyJob = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.dateFrom(DATE_FROM)
				.dateTo(DAILY_DATE_TO)
				.runType(ScrapeJobRunType.DAILY_PREVIOUS_DAY)
				.status(ScrapeJobStatus.PENDING)
				.build();
		when(sourceService.findEnabledEntities()).thenReturn(List.of(source));
		when(keywordService.findEnabledEntities()).thenReturn(List.of(keyword));
		when(scrapeJobService.hasJobForRunType(
				source,
				keyword,
				DATE_FROM,
				DAILY_DATE_TO,
				ScrapeJobRunType.DAILY_PREVIOUS_DAY
		)).thenReturn(false, true);
		when(scrapeJobService.hasActiveJob(source, keyword, DATE_FROM, DAILY_DATE_TO)).thenReturn(false);
		when(scrapeJobService.createPendingJob(
				source,
				keyword,
				DATE_FROM,
				DAILY_DATE_TO,
				ScrapeJobRunType.DAILY_PREVIOUS_DAY
		)).thenReturn(dailyJob);
		when(scrapeJobService.markRunning(dailyJob.getId())).thenReturn(dailyJob);
		when(scraperRunner.run(dailyJob)).thenReturn(ScraperResult.empty());

		DailyScrapeRunResponseDTO firstRun = orchestrator.createAndRunDailyPreviousDayJobs();
		DailyScrapeRunResponseDTO secondRun = orchestrator.createAndRunDailyPreviousDayJobs();

		assertThat(firstRun.jobsCreated()).isEqualTo(1);
		assertThat(firstRun.jobsSkipped()).isZero();
		assertThat(secondRun.jobsCreated()).isZero();
		assertThat(secondRun.jobsSucceeded()).isZero();
		assertThat(secondRun.jobsSkipped()).isEqualTo(1);
		verify(scrapeJobService, times(1)).createPendingJob(
				source,
				keyword,
				DATE_FROM,
				DAILY_DATE_TO,
				ScrapeJobRunType.DAILY_PREVIOUS_DAY
		);
	}

	@ParameterizedTest
	@EnumSource(ScrapeJobStatus.class)
	void existingDailyPreviousDayJobBlocksNewJobForAnyStatus(ScrapeJobStatus existingStatus) {
		ScrapeJob existingJob = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.dateFrom(DATE_FROM)
				.dateTo(DAILY_DATE_TO)
				.runType(ScrapeJobRunType.DAILY_PREVIOUS_DAY)
				.status(existingStatus)
				.build();
		when(sourceService.findEnabledEntities()).thenReturn(List.of(source));
		when(keywordService.findEnabledEntities()).thenReturn(List.of(keyword));
		when(scrapeJobService.hasJobForRunType(
				existingJob.getSource(),
				existingJob.getKeyword(),
				existingJob.getDateFrom(),
				existingJob.getDateTo(),
				existingJob.getRunType()
		)).thenReturn(true);

		DailyScrapeRunResponseDTO response = orchestrator.createAndRunDailyPreviousDayJobs();

		assertThat(response.jobsCreated()).isZero();
		assertThat(response.jobsSucceeded()).isZero();
		assertThat(response.jobsFailed()).isZero();
		assertThat(response.jobsSkipped()).isEqualTo(1);
		verify(scrapeJobService, never()).hasActiveJob(source, keyword, DATE_FROM, DAILY_DATE_TO);
		verify(scrapeJobService, never()).createPendingJob(
				source,
				keyword,
				DATE_FROM,
				DAILY_DATE_TO,
				ScrapeJobRunType.DAILY_PREVIOUS_DAY
		);
	}
}
