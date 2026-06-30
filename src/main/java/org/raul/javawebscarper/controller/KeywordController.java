package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.api.common.PageRequestFactory;
import org.raul.javawebscarper.api.common.PageResponse;
import org.raul.javawebscarper.api.keyword.KeywordRequest;
import org.raul.javawebscarper.api.keyword.KeywordResponse;
import org.raul.javawebscarper.service.KeywordService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/keywords")
@RequiredArgsConstructor
public class KeywordController {

	private final KeywordService keywordService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public KeywordResponse create(@Valid @RequestBody KeywordRequest request) {
		return keywordService.create(request);
	}

	@GetMapping
	public PageResponse<KeywordResponse> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return keywordService.findAll(pageable);
	}

	@GetMapping("/{id}")
	public KeywordResponse findById(@PathVariable Integer id) {
		return keywordService.findById(id);
	}

	@PutMapping("/{id}")
	public KeywordResponse update(
			@PathVariable Integer id,
			@Valid @RequestBody KeywordRequest request
	) {
		return keywordService.update(id, request);
	}

	@PatchMapping("/{id}/enable")
	public KeywordResponse enable(@PathVariable Integer id) {
		return keywordService.enable(id);
	}

	@PatchMapping("/{id}/disable")
	public KeywordResponse disable(@PathVariable Integer id) {
		return keywordService.disable(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Integer id) {
		keywordService.delete(id);
	}
}

