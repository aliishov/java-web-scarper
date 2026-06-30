package org.raul.javawebscarper.api.scrapejob;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.api.common.PageRequestFactory;
import org.raul.javawebscarper.api.common.PageResponse;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/scrape-jobs")
@RequiredArgsConstructor
public class ScrapeJobController {

	private final ScrapeJobService scrapeJobService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ScrapeJobResponse create(@Valid @RequestBody ScrapeJobRequest request) {
		return scrapeJobService.create(request);
	}

	@GetMapping
	public PageResponse<ScrapeJobResponse> findAll(
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
		return scrapeJobService.findAll(sourceId, keywordId, status, dateFrom, dateTo, pageable);
	}

	@GetMapping("/{id}")
	public ScrapeJobResponse findById(@PathVariable UUID id) {
		return scrapeJobService.findById(id);
	}

	@GetMapping("/by-source/{sourceId}")
	public PageResponse<ScrapeJobResponse> findBySource(
			@PathVariable Integer sourceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return scrapeJobService.findBySource(sourceId, pageable);
	}

	@GetMapping("/by-status/{status}")
	public PageResponse<ScrapeJobResponse> findByStatus(
			@PathVariable ScrapeJobStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return scrapeJobService.findByStatus(status, pageable);
	}

	@PatchMapping("/{id}/start")
	public ScrapeJobResponse start(@PathVariable UUID id) {
		return scrapeJobService.start(id);
	}

	@PatchMapping("/{id}/complete")
	public ScrapeJobResponse complete(
			@PathVariable UUID id,
			@Valid @RequestBody ScrapeJobCompleteRequest request
	) {
		return scrapeJobService.complete(id, request);
	}

	@PatchMapping("/{id}/fail")
	public ScrapeJobResponse fail(
			@PathVariable UUID id,
			@Valid @RequestBody ScrapeJobFailRequest request
	) {
		return scrapeJobService.fail(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		scrapeJobService.delete(id);
	}
}
