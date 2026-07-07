package org.raul.javawebscarper.service;

import jakarta.persistence.criteria.Join;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.post.PostKeywordRequestDTO;
import org.raul.javawebscarper.dto.request.post.PostMediaRequestDTO;
import org.raul.javawebscarper.dto.request.post.CreatePostRequestDTO;
import org.raul.javawebscarper.dto.request.post.UpdatePostRequestDTO;
import org.raul.javawebscarper.dto.response.post.PostResponseDTO;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.exception.DuplicateResourceException;
import org.raul.javawebscarper.exception.ResourceNotFoundException;
import org.raul.javawebscarper.mapper.PostMapper;
import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.PostKeyword;
import org.raul.javawebscarper.model.PostMedia;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.repository.PostRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

	private final PostRepository postRepository;
	private final SourceService sourceService;
	private final AuthorService authorService;
	private final KeywordService keywordService;

	@Transactional
	public PostResponseDTO create(CreatePostRequestDTO request) {
		Source source = sourceService.getEntity(request.sourceId());
		Author author = authorService.getEntity(request.authorId());
		validateAuthorSource(source, author);
		validateDuplicatePost(source, request.externalPostId(), request.postUrl(), request.textHash(), null);

		Post post = PostMapper.toEntity(request, source, author);
		replaceMedia(post, request.media());
		replaceKeywords(post, request.keywords());
		return PostMapper.toResponse(postRepository.save(post));
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<PostResponseDTO> findAll(
			Integer sourceId,
			UUID authorId,
			Integer keywordId,
			OffsetDateTime dateFrom,
			OffsetDateTime dateTo,
			String search,
			Pageable pageable
	) {
		validateDateRange(dateFrom, dateTo);
		Specification<Post> specification = buildSpecification(sourceId, authorId, keywordId, dateFrom, dateTo, search);
		return PageResponseDTO.from(postRepository.findAll(specification, pageable), PostMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public PostResponseDTO findById(UUID id) {
		return PostMapper.toResponse(getEntity(id));
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<PostResponseDTO> findBySource(Integer sourceId, Pageable pageable) {
		Source source = sourceService.getEntity(sourceId);
		return PageResponseDTO.from(postRepository.findBySource(source, pageable), PostMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<PostResponseDTO> findByAuthor(UUID authorId, Pageable pageable) {
		Author author = authorService.getEntity(authorId);
		return PageResponseDTO.from(postRepository.findByAuthor(author, pageable), PostMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<PostResponseDTO> findByKeyword(Integer keywordId, Pageable pageable) {
		Keyword keyword = keywordService.getEntity(keywordId);
		return PageResponseDTO.from(postRepository.findByKeyword(keyword, pageable), PostMapper::toResponse);
	}

	@Transactional
	public PostResponseDTO update(UUID id, UpdatePostRequestDTO request) {
		Post post = getEntity(id);
		Source source = sourceService.getEntity(request.sourceId());
		Author author = authorService.getEntity(request.authorId());
		validateAuthorSource(source, author);
		validateDuplicatePost(source, request.externalPostId(), request.postUrl(), request.textHash(), id);

		PostMapper.updateEntity(post, request, source, author);
		replaceMedia(post, request.media());
		replaceKeywords(post, request.keywords());
		return PostMapper.toResponse(post);
	}

	@Transactional
	public void delete(UUID id) {
		Post post = getEntity(id);
		postRepository.delete(post);
	}

	@Transactional(readOnly = true)
	public Post getEntity(UUID id) {
		return postRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Post with id '%s' was not found".formatted(id)));
	}

	private void replaceMedia(Post post, List<PostMediaRequestDTO> mediaRequests) {
		post.getMedia().clear();
		if (mediaRequests == null || mediaRequests.isEmpty()) {
			return;
		}

		Set<Integer> positions = new HashSet<>();
		Set<String> mediaUrls = new HashSet<>();
		for (PostMediaRequestDTO mediaRequest : mediaRequests) {
			String mediaUrl = mediaRequest.mediaUrl().trim();
			if (!positions.add(mediaRequest.position())) {
				throw new BadRequestException("Duplicate media position '%s'".formatted(mediaRequest.position()));
			}
			if (!mediaUrls.add(mediaUrl)) {
				throw new BadRequestException("Duplicate media URL '%s'".formatted(mediaUrl));
			}

			post.addMedia(PostMedia.builder()
					.mediaUrl(mediaUrl)
					.mediaType(mediaRequest.mediaType())
					.position(mediaRequest.position())
					.build());
		}
	}

	private void replaceKeywords(Post post, List<PostKeywordRequestDTO> keywordRequests) {
		if (keywordRequests == null || keywordRequests.isEmpty()) {
			removeKeywordsNotIn(post, Set.of());
			return;
		}

		Map<Integer, PostKeywordRequestDTO> keywordRequestsById = new LinkedHashMap<>();
		for (PostKeywordRequestDTO keywordRequest : keywordRequests) {
			if (keywordRequestsById.putIfAbsent(keywordRequest.keywordId(), keywordRequest) != null) {
				throw new BadRequestException("Duplicate keyword id '%s'".formatted(keywordRequest.keywordId()));
			}
		}

		removeKeywordsNotIn(post, keywordRequestsById.keySet());
		Set<Integer> existingKeywordIds = new HashSet<>();
		for (PostKeyword postKeyword : post.getKeywords()) {
			existingKeywordIds.add(postKeyword.getKeyword().getId());
		}

		for (PostKeywordRequestDTO keywordRequest : keywordRequestsById.values()) {
			if (existingKeywordIds.contains(keywordRequest.keywordId())) {
				continue;
			}
			Keyword keyword = keywordService.getEntity(keywordRequest.keywordId());
			post.getKeywords().add(PostKeyword.builder()
					.post(post)
					.keyword(keyword)
					.matchedText(nullIfBlank(keywordRequest.matchedText()))
					.build());
		}
	}

	private void removeKeywordsNotIn(Post post, Set<Integer> keywordIdsToKeep) {
		Iterator<PostKeyword> iterator = post.getKeywords().iterator();
		while (iterator.hasNext()) {
			PostKeyword postKeyword = iterator.next();
			if (!keywordIdsToKeep.contains(postKeyword.getKeyword().getId())) {
				iterator.remove();
				postKeyword.setPost(null);
			}
		}
	}

	private void validateAuthorSource(Source source, Author author) {
		if (!author.getSource().getId().equals(source.getId())) {
			throw new BadRequestException("Author does not belong to source '%s'".formatted(source.getCode()));
		}
	}

	private void validateDuplicatePost(
			Source source,
			String externalPostIdValue,
			String postUrlValue,
			String textHashValue,
			UUID currentId
	) {
		String externalPostId = nullIfBlank(externalPostIdValue);
		String postUrl = postUrlValue.trim();
		String textHash = textHashValue.trim();

		if (externalPostId != null && existsExternalPostId(source, externalPostId, currentId)) {
			throw new DuplicateResourceException("Post externalPostId '%s' already exists for source '%s'"
					.formatted(externalPostId, source.getCode()));
		}
		if (existsPostUrl(source, postUrl, currentId)) {
			throw new DuplicateResourceException("Post URL '%s' already exists for source '%s'"
					.formatted(postUrl, source.getCode()));
		}
		if (existsTextHash(source, textHash, currentId)) {
			throw new DuplicateResourceException("Post textHash '%s' already exists for source '%s'"
					.formatted(textHash, source.getCode()));
		}
	}

	private boolean existsExternalPostId(Source source, String externalPostId, UUID currentId) {
		if (currentId == null) {
			return postRepository.existsBySourceAndExternalPostId(source, externalPostId);
		}
		return postRepository.existsBySourceAndExternalPostIdAndIdNot(source, externalPostId, currentId);
	}

	private boolean existsPostUrl(Source source, String postUrl, UUID currentId) {
		if (currentId == null) {
			return postRepository.existsBySourceAndPostUrl(source, postUrl);
		}
		return postRepository.existsBySourceAndPostUrlAndIdNot(source, postUrl, currentId);
	}

	private boolean existsTextHash(Source source, String textHash, UUID currentId) {
		if (currentId == null) {
			return postRepository.existsBySourceAndTextHash(source, textHash);
		}
		return postRepository.existsBySourceAndTextHashAndIdNot(source, textHash, currentId);
	}

	private Specification<Post> buildSpecification(
			Integer sourceId,
			UUID authorId,
			Integer keywordId,
			OffsetDateTime dateFrom,
			OffsetDateTime dateTo,
			String search
	) {
		Specification<Post> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

		if (sourceId != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.equal(root.get("source").get("id"), sourceId));
		}
		if (authorId != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.equal(root.get("author").get("id"), authorId));
		}
		if (keywordId != null) {
			specification = specification.and((root, query, criteriaBuilder) -> {
				query.distinct(true);
				Join<Post, PostKeyword> keywordJoin = root.join("keywords");
				return criteriaBuilder.equal(keywordJoin.get("keyword").get("id"), keywordId);
			});
		}
		if (dateFrom != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.greaterThanOrEqualTo(root.get("postDate"), dateFrom));
		}
		if (dateTo != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.lessThanOrEqualTo(root.get("postDate"), dateTo));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase() + "%";
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.like(criteriaBuilder.lower(root.get("text")), pattern));
		}
		return specification;
	}

	private void validateDateRange(OffsetDateTime dateFrom, OffsetDateTime dateTo) {
		if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
			throw new BadRequestException("dateTo must be greater than or equal to dateFrom");
		}
	}

	private String nullIfBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
