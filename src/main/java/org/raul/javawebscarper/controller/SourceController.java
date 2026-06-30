package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.source.CreateSourceRequestDTO;
import org.raul.javawebscarper.dto.request.source.UpdateSourceRequestDTO;
import org.raul.javawebscarper.dto.response.source.SourceResponseDTO;
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
	public SourceResponseDTO create(@Valid @RequestBody CreateSourceRequestDTO request) {
		return sourceService.create(request);
	}

	@GetMapping
	public PageResponseDTO<SourceResponseDTO> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return sourceService.findAll(pageable);
	}

	@GetMapping("/{id}")
	public SourceResponseDTO findById(@PathVariable Integer id) {
		return sourceService.findById(id);
	}

	@PutMapping("/{id}")
	public SourceResponseDTO update(
			@PathVariable Integer id,
			@Valid @RequestBody UpdateSourceRequestDTO request
	) {
		return sourceService.update(id, request);
	}

	@PatchMapping("/{id}/enable")
	public SourceResponseDTO enable(@PathVariable Integer id) {
		return sourceService.enable(id);
	}

	@PatchMapping("/{id}/disable")
	public SourceResponseDTO disable(@PathVariable Integer id) {
		return sourceService.disable(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Integer id) {
		sourceService.delete(id);
	}
}
