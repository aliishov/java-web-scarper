package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.post.CreatePostRequestDTO;
import org.raul.javawebscarper.dto.request.post.UpdatePostRequestDTO;
import org.raul.javawebscarper.dto.response.post.PostResponseDTO;
import org.raul.javawebscarper.service.PostService;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PostResponseDTO create(@Valid @RequestBody CreatePostRequestDTO request) {
		return postService.create(request);
	}

	@GetMapping
	public PageResponseDTO<PostResponseDTO> findAll(
			@RequestParam(required = false) Integer sourceId,
			@RequestParam(required = false) UUID authorId,
			@RequestParam(required = false) Integer keywordId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateTo,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String text,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "postDate") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		String searchText = search == null ? text : search;
		return postService.findAll(sourceId, authorId, keywordId, dateFrom, dateTo, searchText, pageable);
	}

	@GetMapping("/{id}")
	public PostResponseDTO findById(@PathVariable UUID id) {
		return postService.findById(id);
	}

	@GetMapping("/by-source/{sourceId}")
	public PageResponseDTO<PostResponseDTO> findBySource(
			@PathVariable Integer sourceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "postDate") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return postService.findBySource(sourceId, pageable);
	}

	@GetMapping("/by-author/{authorId}")
	public PageResponseDTO<PostResponseDTO> findByAuthor(
			@PathVariable UUID authorId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "postDate") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return postService.findByAuthor(authorId, pageable);
	}

	@GetMapping("/by-keyword/{keywordId}")
	public PageResponseDTO<PostResponseDTO> findByKeyword(
			@PathVariable Integer keywordId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "postDate") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return postService.findByKeyword(keywordId, pageable);
	}

	@PutMapping("/{id}")
	public PostResponseDTO update(
			@PathVariable UUID id,
			@Valid @RequestBody UpdatePostRequestDTO request
	) {
		return postService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		postService.delete(id);
	}
}
