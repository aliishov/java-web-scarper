package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.keyword.CreateKeywordRequestDTO;
import org.raul.javawebscarper.dto.request.keyword.UpdateKeywordRequestDTO;
import org.raul.javawebscarper.dto.response.keyword.KeywordResponseDTO;
import org.raul.javawebscarper.service.KeywordService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/keywords")
@RequiredArgsConstructor
public class KeywordController {

	private final KeywordService keywordService;

	@PostMapping
	public ResponseEntity<BaseResponseDTO<KeywordResponseDTO>> create(@Valid @RequestBody CreateKeywordRequestDTO request) {
		KeywordResponseDTO response = keywordService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(BaseResponseDTO.success(response, "Keyword created successfully"));
	}

	@GetMapping
	public ResponseEntity<BaseResponseDTO<PageResponseDTO<KeywordResponseDTO>>> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return ResponseEntity.ok(BaseResponseDTO.success(keywordService.findAll(pageable)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<KeywordResponseDTO>> findById(@PathVariable Integer id) {
		return ResponseEntity.ok(BaseResponseDTO.success(keywordService.findById(id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<KeywordResponseDTO>> update(
			@PathVariable Integer id,
			@Valid @RequestBody UpdateKeywordRequestDTO request
	) {
		KeywordResponseDTO response = keywordService.update(id, request);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Keyword updated successfully"));
	}

	@PatchMapping("/{id}/enable")
	public ResponseEntity<BaseResponseDTO<KeywordResponseDTO>> enable(@PathVariable Integer id) {
		KeywordResponseDTO response = keywordService.enable(id);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Keyword enabled successfully"));
	}

	@PatchMapping("/{id}/disable")
	public ResponseEntity<BaseResponseDTO<KeywordResponseDTO>> disable(@PathVariable Integer id) {
		KeywordResponseDTO response = keywordService.disable(id);
		return ResponseEntity.ok(BaseResponseDTO.success(response, "Keyword disabled successfully"));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<BaseResponseDTO<Void>> delete(@PathVariable Integer id) {
		keywordService.delete(id);
		return ResponseEntity.ok(BaseResponseDTO.success("Keyword deleted successfully"));
	}
}
