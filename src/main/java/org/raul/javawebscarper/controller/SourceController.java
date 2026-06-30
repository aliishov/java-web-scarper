package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.api.common.PageRequestFactory;
import org.raul.javawebscarper.api.common.PageResponse;
import org.raul.javawebscarper.api.source.SourceRequest;
import org.raul.javawebscarper.api.source.SourceResponse;
import org.raul.javawebscarper.service.SourceService;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
public class SourceController {

	private final SourceService sourceService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SourceResponse create(@Valid @RequestBody SourceRequest request) {
		return sourceService.create(request);
	}

	@GetMapping
	public PageResponse<SourceResponse> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return sourceService.findAll(pageable);
	}

	@GetMapping("/{id}")
	public SourceResponse findById(@PathVariable Integer id) {
		return sourceService.findById(id);
	}

	@PutMapping("/{id}")
	public SourceResponse update(
			@PathVariable Integer id,
			@Valid @RequestBody SourceRequest request
	) {
		return sourceService.update(id, request);
	}

	@PatchMapping("/{id}/enable")
	public SourceResponse enable(@PathVariable Integer id) {
		return sourceService.enable(id);
	}

	@PatchMapping("/{id}/disable")
	public SourceResponse disable(@PathVariable Integer id) {
		return sourceService.disable(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Integer id) {
		sourceService.delete(id);
	}
}

