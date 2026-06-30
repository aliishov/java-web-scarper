package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.source.CreateSourceRequestDTO;
import org.raul.javawebscarper.dto.request.source.UpdateSourceRequestDTO;
import org.raul.javawebscarper.dto.response.source.SourceResponseDTO;
import org.raul.javawebscarper.service.SourceService;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
public class SourceController {

	private final SourceService sourceService;

	@PostMapping
	public ResponseEntity<BaseResponseDTO<SourceResponseDTO>> create(@Valid @RequestBody CreateSourceRequestDTO request) {
		SourceResponseDTO response = sourceService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(BaseResponseDTO.success(response, "Source created successfully"));
	}

	@GetMapping
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<SourceResponseDTO>>> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(sourceService.findAll(pageable)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<SourceResponseDTO>> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(BaseResponseDTO.success(sourceService.findById(id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<SourceResponseDTO>> update(
			@PathVariable Integer id,
			@Valid @RequestBody UpdateSourceRequestDTO request
	) {
		SourceResponseDTO response = sourceService.update(id, request);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Source updated successfully"));
	}

	@PatchMapping("/{id}/enable")
	public ResponseEntity<BaseResponseDTO<SourceResponseDTO>> enable(@PathVariable Integer id) {
		SourceResponseDTO response = sourceService.enable(id);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Source enabled successfully"));
	}

	@PatchMapping("/{id}/disable")
	public ResponseEntity<BaseResponseDTO<SourceResponseDTO>> disable(@PathVariable Integer id) {
		SourceResponseDTO response = sourceService.disable(id);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Source disabled successfully"));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<Void>> delete(@PathVariable Integer id) {
		sourceService.delete(id);
		return ResponseEntity.ok(BaseResponseDTO.success("Source deleted successfully"));
	}
}
