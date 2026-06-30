package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.request.author.CreateAuthorRequestDTO;
import org.raul.javawebscarper.dto.request.author.UpdateAuthorRequestDTO;
import org.raul.javawebscarper.dto.response.author.AuthorResponseDTO;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.exception.DuplicateResourceException;
import org.raul.javawebscarper.exception.ResourceNotFoundException;
import org.raul.javawebscarper.mapper.AuthorMapper;
import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.repository.AuthorRepository;
import org.raul.javawebscarper.repository.PostRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorService {

	private final AuthorRepository authorRepository;
	private final PostRepository postRepository;
	private final SourceService sourceService;
	private final AuthorMapper authorMapper;

	@Transactional
	public AuthorResponseDTO create(CreateAuthorRequestDTO request) {
		Source source = sourceService.getEntity(request.sourceId());
		validateUniqueNaturalKeys(source, request.externalId(), request.username(), request.profileUrl(), null);
		Author author = authorMapper.toEntity(request, source);
		return authorMapper.toResponse(authorRepository.save(author));
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<AuthorResponseDTO> findAll(Pageable pageable) {
		return PageResponseDTO.from(authorRepository.findAll(pageable), authorMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<AuthorResponseDTO> findBySource(Integer sourceId, Pageable pageable) {
		Source source = sourceService.getEntity(sourceId);
		return PageResponseDTO.from(authorRepository.findBySource(source, pageable), authorMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public AuthorResponseDTO findById(UUID id) {
		return authorMapper.toResponse(getEntity(id));
	}

	@Transactional
	public AuthorResponseDTO update(UUID id, UpdateAuthorRequestDTO request) {
		Author author = getEntity(id);
		Source source = sourceService.getEntity(request.sourceId());
		validateUniqueNaturalKeys(source, request.externalId(), request.username(), request.profileUrl(), id);
		authorMapper.updateEntity(author, request, source);
		return authorMapper.toResponse(author);
	}

	@Transactional
	public void delete(UUID id) {
		Author author = getEntity(id);
		if (postRepository.existsByAuthor(author)) {
			throw new BadRequestException("Author has related posts and cannot be deleted");
		}
		authorRepository.delete(author);
	}

	@Transactional(readOnly = true)
	public Author getEntity(UUID id) {
		return authorRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Author with id '%s' was not found".formatted(id)));
	}

	private void validateUniqueNaturalKeys(
			Source source,
			String externalIdValue,
			String usernameValue,
			String profileUrlValue,
			UUID currentId
	) {
		String externalId = nullIfBlank(externalIdValue);
		String username = usernameValue.trim();
		String profileUrl = nullIfBlank(profileUrlValue);

		if (externalId != null && existsExternalId(source, externalId, currentId)) {
			throw new DuplicateResourceException("Author externalId '%s' already exists for source '%s'"
					.formatted(externalId, source.getCode()));
		}
		if (existsUsername(source, username, currentId)) {
			throw new DuplicateResourceException("Author username '%s' already exists for source '%s'"
					.formatted(username, source.getCode()));
		}
		if (profileUrl != null && existsProfileUrl(profileUrl, currentId)) {
			throw new DuplicateResourceException("Author profileUrl '%s' already exists".formatted(profileUrl));
		}
	}

	private boolean existsExternalId(Source source, String externalId, UUID currentId) {
		if (currentId == null) {
			return authorRepository.existsBySourceAndExternalId(source, externalId);
		}
		return authorRepository.existsBySourceAndExternalIdAndIdNot(source, externalId, currentId);
	}

	private boolean existsUsername(Source source, String username, UUID currentId) {
		if (currentId == null) {
			return authorRepository.existsBySourceAndUsername(source, username);
		}
		return authorRepository.existsBySourceAndUsernameAndIdNot(source, username, currentId);
	}

	private boolean existsProfileUrl(String profileUrl, UUID currentId) {
		if (currentId == null) {
			return authorRepository.existsByProfileUrl(profileUrl);
		}
		return authorRepository.existsByProfileUrlAndIdNot(profileUrl, currentId);
	}

	private String nullIfBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
