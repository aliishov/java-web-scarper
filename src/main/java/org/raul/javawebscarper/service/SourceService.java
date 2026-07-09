package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.source.CreateSourceRequestDTO;
import org.raul.javawebscarper.dto.request.source.UpdateSourceRequestDTO;
import org.raul.javawebscarper.dto.response.source.SourceResponseDTO;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.exception.DuplicateResourceException;
import org.raul.javawebscarper.exception.ResourceNotFoundException;
import org.raul.javawebscarper.mapper.SourceMapper;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.repository.AuthorRepository;
import org.raul.javawebscarper.repository.PostRepository;
import org.raul.javawebscarper.repository.ScrapeJobRepository;
import org.raul.javawebscarper.repository.SourceRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SourceService {

	private final SourceRepository sourceRepository;
	private final AuthorRepository authorRepository;
	private final PostRepository postRepository;
	private final ScrapeJobRepository scrapeJobRepository;

	@Transactional
	public SourceResponseDTO create(CreateSourceRequestDTO request) {
		String code = normalize(request.code());
		if (sourceRepository.existsByCode(code)) {
			throw new DuplicateResourceException("Source with code '%s' already exists".formatted(code));
		}
		Source source = SourceMapper.toEntity(request);
		validateSupportedLanguages(source.isEnabled(), source.getSupportedLanguages());
		return SourceMapper.toResponse(sourceRepository.save(source));
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<SourceResponseDTO> findAll(Pageable pageable) {
		return PageResponseDTO.from(sourceRepository.findAll(pageable), SourceMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public SourceResponseDTO findById(Integer id) {
		return SourceMapper.toResponse(getEntity(id));
	}

	@Transactional(readOnly = true)
	public List<Source> findEnabledEntities() {
		return sourceRepository.findByEnabledTrue();
	}

	@Transactional
	public SourceResponseDTO update(Integer id, UpdateSourceRequestDTO request) {
		Source source = getEntity(id);
		String code = normalize(request.code());
		if (sourceRepository.existsByCodeAndIdNot(code, id)) {
			throw new DuplicateResourceException("Source with code '%s' already exists".formatted(code));
		}
		SourceMapper.updateEntity(source, request);
		validateSupportedLanguages(source.isEnabled(), source.getSupportedLanguages());
		return SourceMapper.toResponse(source);
	}

	@Transactional
	public SourceResponseDTO enable(Integer id) {
		Source source = getEntity(id);
		validateSupportedLanguages(true, source.getSupportedLanguages());
		source.setEnabled(true);
		return SourceMapper.toResponse(source);
	}

	@Transactional
	public SourceResponseDTO disable(Integer id) {
		Source source = getEntity(id);
		source.setEnabled(false);
		return SourceMapper.toResponse(source);
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

	private void validateSupportedLanguages(boolean enabled, Set<Language> supportedLanguages) {
		if (enabled && (supportedLanguages == null || supportedLanguages.isEmpty())) {
			throw new BadRequestException("Enabled source must support at least one language");
		}
	}
}
