package org.raul.javawebscarper.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.raul.javawebscarper.config.DailyScrapingProperties;
import org.raul.javawebscarper.dto.response.scrapejob.DailyScrapeRunResponseDTO;
import org.raul.javawebscarper.orchestrator.ScrapeJobOrchestrator;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPreviousDayScrapingSchedulerTests {

	@Mock
	private ScrapeJobOrchestrator orchestrator;

	private DailyScrapingProperties properties;
	private DailyPreviousDayScrapingScheduler scheduler;

	@BeforeEach
	void setUp() {
		properties = new DailyScrapingProperties();
		scheduler = new DailyPreviousDayScrapingScheduler(properties, orchestrator);
	}

	@Test
	void disabledSchedulerDoesNotRun() {
		properties.setEnabled(false);

		scheduler.runDailyPreviousDayScraping();

		verifyNoInteractions(orchestrator);
	}

	@Test
	void enabledSchedulerRunsOrchestrator() {
		when(orchestrator.createAndRunDailyPreviousDayJobs()).thenReturn(emptySummary());

		scheduler.runDailyPreviousDayScraping();

		verify(orchestrator).createAndRunDailyPreviousDayJobs();
	}

	@Test
	void runOnStartupRespectsFlag() {
		properties.setRunOnStartup(false);

		scheduler.runOnStartup();

		verify(orchestrator, never()).createAndRunDailyPreviousDayJobs();
	}

	@Test
	void runOnStartupRunsWhenEnabled() {
		properties.setRunOnStartup(true);
		when(orchestrator.createAndRunDailyPreviousDayJobs()).thenReturn(emptySummary());

		scheduler.runOnStartup();

		verify(orchestrator).createAndRunDailyPreviousDayJobs();
	}

	private DailyScrapeRunResponseDTO emptySummary() {
		OffsetDateTime dateFrom = OffsetDateTime.parse("2026-07-07T00:00+04:00");
		OffsetDateTime dateTo = OffsetDateTime.parse("2026-07-07T23:59:59.999999999+04:00");
		return new DailyScrapeRunResponseDTO(
				dateFrom,
				dateTo,
				"Asia/Baku",
				0,
				0,
				0,
				0,
				0,
				0,
				0,
				0,
				dateFrom,
				dateTo
		);
	}
}
