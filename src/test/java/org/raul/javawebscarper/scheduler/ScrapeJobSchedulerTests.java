package org.raul.javawebscarper.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.raul.javawebscarper.config.ScrapeSchedulerProperties;
import org.raul.javawebscarper.dto.response.scrapejob.ScheduledScrapeRunResponseDTO;
import org.raul.javawebscarper.orchestrator.ScrapeJobOrchestrator;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapeJobSchedulerTests {

	@Mock
	private ScrapeJobOrchestrator orchestrator;

	private ScrapeSchedulerProperties properties;
	private ScrapeJobScheduler scheduler;

	@BeforeEach
	void setUp() {
		properties = new ScrapeSchedulerProperties();
		scheduler = new ScrapeJobScheduler(properties, orchestrator);
	}

	@Test
	void disabledSchedulerDoesNotRun() {
		properties.setEnabled(false);

		scheduler.runScheduledScraping();

		verifyNoInteractions(orchestrator);
	}

	@Test
	void enabledSchedulerRunsOrchestrator() {
		when(orchestrator.createAndRunScheduledJobs()).thenReturn(emptySummary());

		scheduler.runScheduledScraping();

		verify(orchestrator).createAndRunScheduledJobs();
	}

	@Test
	void runOnStartupRespectsFlag() {
		properties.setRunOnStartup(false);

		scheduler.runOnStartup();

		verify(orchestrator, never()).createAndRunScheduledJobs();
	}

	@Test
	void runOnStartupRunsWhenEnabled() {
		properties.setRunOnStartup(true);
		when(orchestrator.createAndRunScheduledJobs()).thenReturn(emptySummary());

		scheduler.runOnStartup();

		verify(orchestrator).createAndRunScheduledJobs();
	}

	private ScheduledScrapeRunResponseDTO emptySummary() {
		OffsetDateTime now = OffsetDateTime.parse("2026-07-01T08:30:00Z");
		return new ScheduledScrapeRunResponseDTO(0, 0, 0, 0, 0, 0, now, now);
	}
}
