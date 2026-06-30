package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.api.common.PageResponse;
import org.raul.javawebscarper.api.keyword.KeywordRequest;
import org.raul.javawebscarper.api.keyword.KeywordResponse;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.exception.DuplicateResourceException;
import org.raul.javawebscarper.exception.ResourceNotFoundException;
import org.raul.javawebscarper.mapper.KeywordMapper;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.repository.KeywordRepository;
import org.raul.javawebscarper.repository.PostKeywordRepository;
import org.raul.javawebscarper.repository.ScrapeJobRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KeywordService {

	private final KeywordRepository keywordRepository;
	private final PostKeywordRepository postKeywordRepository;
	private final ScrapeJobRepository scrapeJobRepository;
	private final KeywordMapper keywordMapper;

	@Transactional
	public KeywordResponse create(KeywordRequest request) {
		String word = normalize(request.word());
		if (keywordRepository.existsByWord(word)) {
			throw new DuplicateResourceException("Keyword '%s' already exists".formatted(word));
		}
		Keyword keyword = keywordMapper.toEntity(request);
		return keywordMapper.toResponse(keywordRepository.save(keyword));
	}

	@Transactional(readOnly = true)
	public PageResponse<KeywordResponse> findAll(Pageable pageable) {
		return PageResponse.from(keywordRepository.findAll(pageable), keywordMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public KeywordResponse findById(Integer id) {
		return keywordMapper.toResponse(getEntity(id));
	}

	@Transactional
	public KeywordResponse update(Integer id, KeywordRequest request) {
		Keyword keyword = getEntity(id);
		String word = normalize(request.word());
		if (keywordRepository.existsByWordAndIdNot(word, id)) {
			throw new DuplicateResourceException("Keyword '%s' already exists".formatted(word));
		}
		keywordMapper.updateEntity(keyword, request);
		return keywordMapper.toResponse(keyword);
	}

	@Transactional
	public KeywordResponse enable(Integer id) {
		Keyword keyword = getEntity(id);
		keyword.setEnabled(true);
		return keywordMapper.toResponse(keyword);
	}

	@Transactional
	public KeywordResponse disable(Integer id) {
		Keyword keyword = getEntity(id);
		keyword.setEnabled(false);
		return keywordMapper.toResponse(keyword);
	}

	@Transactional
	public void delete(Integer id) {
		Keyword keyword = getEntity(id);
		if (postKeywordRepository.existsByKeyword(keyword) || scrapeJobRepository.existsByKeyword(keyword)) {
			throw new BadRequestException("Keyword has related data and cannot be deleted; disable it instead");
		}
		keywordRepository.delete(keyword);
	}

	@Transactional(readOnly = true)
	public Keyword getEntity(Integer id) {
		return keywordRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Keyword with id '%s' was not found".formatted(id)));
	}

	private String normalize(String value) {
		return value.trim().toLowerCase();
	}
}
