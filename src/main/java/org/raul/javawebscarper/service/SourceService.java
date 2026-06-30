package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.api.common.PageResponse;
import org.raul.javawebscarper.api.source.SourceRequest;
import org.raul.javawebscarper.api.source.SourceResponse;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.exception.DuplicateResourceException;
import org.raul.javawebscarper.exception.ResourceNotFoundException;
import org.raul.javawebscarper.mapper.SourceMapper;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.repository.AuthorRepository;
import org.raul.javawebscarper.repository.PostRepository;
import org.raul.javawebscarper.repository.ScrapeJobRepository;
import org.raul.javawebscarper.repository.SourceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SourceService {

	private final SourceRepository sourceRepository;
	private final AuthorRepository authorRepository;
	private final PostRepository postRepository;
	private final ScrapeJobRepository scrapeJobRepository;
	private final SourceMapper sourceMapper;

	@Transactional
	public SourceResponse create(SourceRequest request) {
		String code = normalize(request.code());
		if (sourceRepository.existsByCode(code)) {
			throw new DuplicateResourceException("Source with code '%s' already exists".formatted(code));
		}
		Source source = sourceMapper.toEntity(request);
		return sourceMapper.toResponse(sourceRepository.save(source));
	}

	@Transactional(readOnly = true)
	public PageResponse<SourceResponse> findAll(Pageable pageable) {
		return PageResponse.from(sourceRepository.findAll(pageable), sourceMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public SourceResponse findById(Integer id) {
		return sourceMapper.toResponse(getEntity(id));
	}

	@Transactional
	public SourceResponse update(Integer id, SourceRequest request) {
		Source source = getEntity(id);
		String code = normalize(request.code());
		if (sourceRepository.existsByCodeAndIdNot(code, id)) {
			throw new DuplicateResourceException("Source with code '%s' already exists".formatted(code));
		}
		sourceMapper.updateEntity(source, request);
		return sourceMapper.toResponse(source);
	}

	@Transactional
	public SourceResponse enable(Integer id) {
		Source source = getEntity(id);
		source.setEnabled(true);
		return sourceMapper.toResponse(source);
	}

	@Transactional
	public SourceResponse disable(Integer id) {
		Source source = getEntity(id);
		source.setEnabled(false);
		return sourceMapper.toResponse(source);
	}

	@Transactional
	public void delete(Integer id) {
		Source source = getEntity(id);
		if (authorRepository.existsBySource(source)
				|| postRepository.existsBySource(source)
				|| scrapeJobRepository.existsBySource(source)) {
			throw new BadRequestException("Source has related data and cannot be deleted; disable it instead");
		}
		sourceRepository.delete(source);
	}

	@Transactional(readOnly = true)
	public Source getEntity(Integer id) {
		return sourceRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Source with id '%s' was not found".formatted(id)));
	}

	private String normalize(String value) {
		return value.trim().toLowerCase();
	}
}
