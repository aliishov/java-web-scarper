package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.request.author.CreateAuthorRequestDTO;
import org.raul.javawebscarper.dto.request.author.UpdateAuthorRequestDTO;
import org.raul.javawebscarper.dto.response.author.AuthorResponseDTO;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
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
	public AuthorResponseDTO create(@Valid @RequestBody CreateAuthorRequestDTO request) {
		return authorService.create(request);
	}

	@GetMapping
	public PageResponseDTO<AuthorResponseDTO> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return authorService.findAll(pageable);
	}

	@GetMapping("/{id}")
	public AuthorResponseDTO findById(@PathVariable UUID id) {
		return authorService.findById(id);
	}

	@GetMapping("/by-source/{sourceId}")
	public PageResponseDTO<AuthorResponseDTO> findBySource(
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
	public AuthorResponseDTO update(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateAuthorRequestDTO request
	) {
		return authorService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		authorService.delete(id);
	}
}
