package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.post.CreatePostRequestDTO;
import org.raul.javawebscarper.dto.request.post.UpdatePostRequestDTO;
import org.raul.javawebscarper.dto.response.post.PostResponseDTO;
import org.raul.javawebscarper.service.PostService;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;

	@PostMapping
	public ResponseEntity<BaseResponseDTO<PostResponseDTO>> create(@Valid @RequestBody CreatePostRequestDTO request) {
		PostResponseDTO response = postService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(BaseResponseDTO.success(response, "Post created successfully"));
	}

	@GetMapping
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<PostResponseDTO>>> findAll(
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
		return ResponseEntity.ok(BaseResponseDTO.success(
				postService.findAll(sourceId, authorId, keywordId, dateFrom, dateTo, searchText, pageable)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<PostResponseDTO>> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(BaseResponseDTO.success(postService.findById(id)));
	}

	@GetMapping("/by-source/{sourceId}")
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<PostResponseDTO>>> findBySource(
			@PathVariable Integer sourceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "postDate") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(postService.findBySource(sourceId, pageable)));
	}

	@GetMapping("/by-author/{authorId}")
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<PostResponseDTO>>> findByAuthor(
			@PathVariable UUID authorId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "postDate") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(postService.findByAuthor(authorId, pageable)));
	}

	@GetMapping("/by-keyword/{keywordId}")
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<PostResponseDTO>>> findByKeyword(
			@PathVariable Integer keywordId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "postDate") String sortBy,
			@RequestParam(defaultValue = "DESC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(postService.findByKeyword(keywordId, pageable)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<PostResponseDTO>> update(
			@PathVariable UUID id,
			@Valid @RequestBody UpdatePostRequestDTO request
	) {
		PostResponseDTO response = postService.update(id, request);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Post updated successfully"));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<Void>> delete(@PathVariable UUID id) {
		postService.delete(id);
		return ResponseEntity.ok(BaseResponseDTO.success("Post deleted successfully"));
	}
}
