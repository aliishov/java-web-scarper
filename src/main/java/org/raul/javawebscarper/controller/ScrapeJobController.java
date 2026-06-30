package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.scrapejob.CompleteScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.request.scrapejob.FailScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.request.scrapejob.CreateScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScrapeJobResponseDTO;
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
	public ScrapeJobResponseDTO create(@Valid @RequestBody CreateScrapeJobRequestDTO request) {
		return scrapeJobService.create(request);
	}

	@GetMapping
	public PageResponseDTO<ScrapeJobResponseDTO> findAll(
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
	public ScrapeJobResponseDTO findById(@PathVariable UUID id) {
		return scrapeJobService.findById(id);
	}

	@GetMapping("/by-source/{sourceId}")
	public PageResponseDTO<ScrapeJobResponseDTO> findBySource(
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
	public PageResponseDTO<ScrapeJobResponseDTO> findByStatus(
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
	public ScrapeJobResponseDTO start(@PathVariable UUID id) {
		return scrapeJobService.start(id);
	}

	@PatchMapping("/{id}/complete")
	public ScrapeJobResponseDTO complete(
			@PathVariable UUID id,
			@Valid @RequestBody CompleteScrapeJobRequestDTO request
	) {
		return scrapeJobService.complete(id, request);
	}

	@PatchMapping("/{id}/fail")
	public ScrapeJobResponseDTO fail(
			@PathVariable UUID id,
			@Valid @RequestBody FailScrapeJobRequestDTO request
	) {
		return scrapeJobService.fail(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		scrapeJobService.delete(id);
	}
}
