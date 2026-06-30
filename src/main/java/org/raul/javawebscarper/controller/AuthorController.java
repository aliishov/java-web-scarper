package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.api.author.AuthorRequest;
import org.raul.javawebscarper.api.author.AuthorResponse;
import org.raul.javawebscarper.api.common.PageRequestFactory;
import org.raul.javawebscarper.api.common.PageResponse;
import org.raul.javawebscarper.service.AuthorService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class AuthorController {

	private final AuthorService authorService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AuthorResponse create(@Valid @RequestBody AuthorRequest request) {
		return authorService.create(request);
	}

	@GetMapping
	public PageResponse<AuthorResponse> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return authorService.findAll(pageable);
	}

	@GetMapping("/{id}")
	public AuthorResponse findById(@PathVariable UUID id) {
		return authorService.findById(id);
	}

	@GetMapping("/by-source/{sourceId}")
	public PageResponse<AuthorResponse> findBySource(
			@PathVariable Integer sourceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return authorService.findBySource(sourceId, pageable);
	}

	@PutMapping("/{id}")
	public AuthorResponse update(
			@PathVariable UUID id,
			@Valid @RequestBody AuthorRequest request
	) {
		return authorService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		authorService.delete(id);
	}
}

