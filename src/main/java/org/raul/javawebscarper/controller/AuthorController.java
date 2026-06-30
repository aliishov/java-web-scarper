package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.dto.request.author.CreateAuthorRequestDTO;
import org.raul.javawebscarper.dto.request.author.UpdateAuthorRequestDTO;
import org.raul.javawebscarper.dto.response.author.AuthorResponseDTO;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.service.AuthorService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class AuthorController {

	private final AuthorService authorService;

	@PostMapping
	public ResponseEntity<BaseResponseDTO<AuthorResponseDTO>> create(@Valid @RequestBody CreateAuthorRequestDTO request) {
		AuthorResponseDTO response = authorService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(BaseResponseDTO.success(response, "Author created successfully"));
	}

	@GetMapping
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<AuthorResponseDTO>>> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(authorService.findAll(pageable)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<AuthorResponseDTO>> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(BaseResponseDTO.success(authorService.findById(id)));
	}

	@GetMapping("/by-source/{sourceId}")
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<AuthorResponseDTO>>> findBySource(
			@PathVariable Integer sourceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(authorService.findBySource(sourceId, pageable)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<AuthorResponseDTO>> update(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateAuthorRequestDTO request
	) {
		AuthorResponseDTO response = authorService.update(id, request);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Author updated successfully"));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<Void>> delete(@PathVariable UUID id) {
		authorService.delete(id);
		return ResponseEntity.ok(BaseResponseDTO.success("Author deleted successfully"));
	}
}
