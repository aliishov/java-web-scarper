package org.raul.javawebscarper.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.DailyScrapeRunResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScheduledScrapeRunResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScrapeJobResponseDTO;
import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.raul.javawebscarper.orchestrator.ScrapeJobOrchestrator;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapeJobControllerTests {

	@Mock
	private ScrapeJobService scrapeJobService;

	@Mock
	private ScrapeJobOrchestrator scrapeJobOrchestrator;

	private ScrapeJobController controller;

	@BeforeEach
	void setUp() {
		controller = new ScrapeJobController(scrapeJobService, scrapeJobOrchestrator);
	}

	@Test
	void runScheduledKeepsExistingManualEndpoint() {
		ScheduledScrapeRunResponseDTO summary = new ScheduledScrapeRunResponseDTO(
				1,
				1,
				1,
				1,
				0,
				0,
				OffsetDateTime.parse("2026-07-08T08:00Z"),
				OffsetDateTime.parse("2026-07-08T08:01Z")
		);
		when(scrapeJobOrchestrator.createAndRunScheduledJobs()).thenReturn(summary);

		ResponseEntity<BaseResponseDTO<ScheduledScrapeRunResponseDTO>> response = controller.runScheduled();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().isSuccess()).isTrue();
		assertThat(response.getBody().getData()).isSameAs(summary);
		verify(scrapeJobOrchestrator).createAndRunScheduledJobs();
	}

	@Test
	void runDailyPreviousDayReturnsDailySummary() {
		DailyScrapeRunResponseDTO summary = new DailyScrapeRunResponseDTO(
				OffsetDateTime.parse("2026-07-07T00:00+04:00"),
				OffsetDateTime.parse("2026-07-07T23:59:59.999999999+04:00"),
				"Asia/Baku",
				1,
				1,
				1,
				1,
				0,
				0,
				4,
				3,
				OffsetDateTime.parse("2026-07-08T03:00+04:00"),
				OffsetDateTime.parse("2026-07-08T03:01+04:00")
		);
		when(scrapeJobOrchestrator.createAndRunDailyPreviousDayJobs()).thenReturn(summary);

		ResponseEntity<BaseResponseDTO<DailyScrapeRunResponseDTO>> response = controller.runDailyPreviousDay();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().isSuccess()).isTrue();
		assertThat(response.getBody().getData()).isSameAs(summary);
		assertThat(response.getBody().getData().dateFrom()).isEqualTo(OffsetDateTime.parse("2026-07-07T00:00+04:00"));
		assertThat(response.getBody().getData().dateTo())
				.isEqualTo(OffsetDateTime.parse("2026-07-07T23:59:59.999999999+04:00"));
		verify(scrapeJobOrchestrator).createAndRunDailyPreviousDayJobs();
	}

	@Test
	void runStartsPendingManualJobInBackground() {
		UUID jobId = UUID.randomUUID();
		ScrapeJobResponseDTO completed = new ScrapeJobResponseDTO(
				jobId, 1, "INSTAGRAM", 1, "baku", LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 4),
				ScrapeJobRunType.MANUAL, ScrapeJobStatus.SUCCESS, OffsetDateTime.now(), OffsetDateTime.now(),
				2, 2, null, OffsetDateTime.now(), OffsetDateTime.now());
		when(scrapeJobOrchestrator.startManualJob(jobId)).thenReturn(completed);

		ResponseEntity<BaseResponseDTO<ScrapeJobResponseDTO>> response = controller.run(jobId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getData()).isEqualTo(completed);
		verify(scrapeJobOrchestrator).startManualJob(jobId);
	}
}
