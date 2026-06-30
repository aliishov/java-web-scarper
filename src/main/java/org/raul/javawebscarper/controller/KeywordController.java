package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.util.PageRequestFactory;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.keyword.CreateKeywordRequestDTO;
import org.raul.javawebscarper.dto.request.keyword.UpdateKeywordRequestDTO;
import org.raul.javawebscarper.dto.response.keyword.KeywordResponseDTO;
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
	public KeywordResponseDTO create(@Valid @RequestBody CreateKeywordRequestDTO request) {
		return keywordService.create(request);
	}

	@GetMapping
	public PageResponseDTO<KeywordResponseDTO> findAll(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "id") String sortBy,
			@RequestParam(defaultValue = "ASC") String direction
	) {
		Pageable pageable = PageRequestFactory.create(page, size, sortBy, direction);
		return keywordService.findAll(pageable);
	}

	@GetMapping("/{id}")
	public KeywordResponseDTO findById(@PathVariable Integer id) {
		return keywordService.findById(id);
	}

	@PutMapping("/{id}")
	public KeywordResponseDTO update(
			@PathVariable Integer id,
			@Valid @RequestBody UpdateKeywordRequestDTO request
	) {
		return keywordService.update(id, request);
	}

	@PatchMapping("/{id}/enable")
	public KeywordResponseDTO enable(@PathVariable Integer id) {
		return keywordService.enable(id);
	}

	@PatchMapping("/{id}/disable")
	public KeywordResponseDTO disable(@PathVariable Integer id) {
		return keywordService.disable(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Integer id) {
		keywordService.delete(id);
	}
}
