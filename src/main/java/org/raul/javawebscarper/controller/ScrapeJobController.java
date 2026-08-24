package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.scrapejob.CompleteScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.request.scrapejob.FailScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.request.scrapejob.CreateScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.response.scrapejob.DailyScrapeRunResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScheduledScrapeRunResponseDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScrapeJobResponseDTO;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.raul.javawebscarper.orchestrator.ScrapeJobOrchestrator;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/scrape-jobs")
@RequiredArgsConstructor
public class ScrapeJobController {

	private final ScrapeJobService scrapeJobService;
	private final ScrapeJobOrchestrator scrapeJobOrchestrator;

	@PostMapping
	public ResponseEntity<BaseResponseDTO<ScrapeJobResponseDTO>> create(
			@Valid @RequestBody CreateScrapeJobRequestDTO request
	) {
		ScrapeJobResponseDTO response = scrapeJobService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(BaseResponseDTO.success(response, "Scrape job created successfully"));
	}

	@GetMapping
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<ScrapeJobResponseDTO>>> findAll(
			@RequestParam(required = false) Integer sourceId,
			@RequestParam(required = false) Integer keywordId,
			@RequestParam(required = false) ScrapeJobStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(
				scrapeJobService.findAll(sourceId, keywordId, status, dateFrom, dateTo, pageable)));
	}

	@PostMapping("/run-scheduled")
	public ResponseEntity<BaseResponseDTO<ScheduledScrapeRunResponseDTO>> runScheduled() {
		ScheduledScrapeRunResponseDTO response = scrapeJobOrchestrator.createAndRunScheduledJobs();
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Scheduled scrape run completed successfully"));
	}

	@PostMapping("/run-daily-previous-day")
	public ResponseEntity<BaseResponseDTO<DailyScrapeRunResponseDTO>> runDailyPreviousDay() {
		DailyScrapeRunResponseDTO response = scrapeJobOrchestrator.createAndRunDailyPreviousDayJobs();
		return ResponseEntity.ok(BaseResponseDTO.success(
				response,
				"Daily previous-day scrape run completed successfully"
		));
	}

	@PostMapping("/{id}/run")
	public ResponseEntity<BaseResponseDTO<ScrapeJobResponseDTO>> run(@PathVariable UUID id) {
		ScrapeJobResponseDTO response = scrapeJobOrchestrator.startManualJob(id);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(BaseResponseDTO.success(response,
				"Scrape job started successfully"));
	}

	@GetMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<ScrapeJobResponseDTO>> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(BaseResponseDTO.success(scrapeJobService.findById(id)));
	}

	@GetMapping("/by-source/{sourceId}")
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<ScrapeJobResponseDTO>>> findBySource(
			@PathVariable Integer sourceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(scrapeJobService.findBySource(sourceId, pageable)));
	}

	@GetMapping("/by-status/{status}")
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<ScrapeJobResponseDTO>>> findByStatus(
			@PathVariable ScrapeJobStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(scrapeJobService.findByStatus(status, pageable)));
	}

	@PatchMapping("/{id}/start")
	public ResponseEntity<BaseResponseDTO<ScrapeJobResponseDTO>> start(@PathVariable UUID id) {
		ScrapeJobResponseDTO response = scrapeJobService.start(id);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Scrape job started successfully"));
	}

	@PatchMapping("/{id}/complete")
	public ResponseEntity<BaseResponseDTO<ScrapeJobResponseDTO>> complete(
			@PathVariable UUID id,
			@Valid @RequestBody CompleteScrapeJobRequestDTO request
	) {
		ScrapeJobResponseDTO response = scrapeJobService.complete(id, request);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Scrape job completed successfully"));
	}

	@PatchMapping("/{id}/fail")
	public ResponseEntity<BaseResponseDTO<ScrapeJobResponseDTO>> fail(
			@PathVariable UUID id,
			@Valid @RequestBody FailScrapeJobRequestDTO request
	) {
		ScrapeJobResponseDTO response = scrapeJobService.fail(id, request);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Scrape job failed successfully"));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<Void>> delete(@PathVariable UUID id) {
		scrapeJobService.delete(id);
		return ResponseEntity.ok(BaseResponseDTO.success("Scrape job deleted successfully"));
	}
}
