package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.PostKeyword;
import org.raul.javawebscarper.model.PostMedia;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.repository.AuthorRepository;
import org.raul.javawebscarper.repository.PostKeywordRepository;
import org.raul.javawebscarper.repository.PostRepository;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.support.TextHashGenerator;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;
import org.raul.javawebscarper.scraper.support.ScraperClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapedPostIngestionService {

	private final AuthorRepository authorRepository;
	private final PostRepository postRepository;
	private final PostKeywordRepository postKeywordRepository;
	private final ScraperClock scraperClock;

	@Transactional
	public ScrapedPostIngestionResult ingest(ScraperExecutionContext context, ScraperExecutionResult result) {
		if (result == null || result.posts().isEmpty()) {
			return ScrapedPostIngestionResult.empty();
		}

		IngestionCounters counters = new IngestionCounters(result.posts().size());
		Set<String> seenPayloadKeys = new HashSet<>();
		for (ScrapedPostDTO scrapedPost : result.posts()) {
			try {
				NormalizedScrapedPost normalizedPost = normalizePost(context.source(), scrapedPost);
				if (!seenPayloadKeys.add(normalizedPost.deduplicationKey())) {
					counters.skip("Duplicate scraped post in execution result: " + normalizedPost.postUrl());
					continue;
				}
				IngestedPostOutcome outcome = ingestPost(context, normalizedPost);
				counters.add(outcome);
			} catch (RuntimeException exception) {
				String postUrl = scrapedPost == null ? null : scrapedPost.postUrl();
				counters.skip("Failed to ingest scraped post '%s': %s".formatted(postUrl, exception.getMessage()));
				log.warn("Failed to ingest scraped post: postUrl={}, error={}", postUrl, exception.getMessage());
			}
		}

		ScrapedPostIngestionResult ingestionResult = counters.toResult();
		log.info(
				"Scraped post ingestion finished: received={}, created={}, updated={}, skipped={}, "
						+ "mediaCreated={}, keywordsLinked={}, errors={}",
				ingestionResult.postsReceived(),
				ingestionResult.postsCreated(),
				ingestionResult.postsUpdated(),
				ingestionResult.postsSkipped(),
				ingestionResult.mediaCreated(),
				ingestionResult.keywordsLinked(),
				ingestionResult.errors().size()
		);
		return ingestionResult;
	}

	private IngestedPostOutcome ingestPost(ScraperExecutionContext context, NormalizedScrapedPost normalizedPost) {
		Author author = resolveAuthor(context.source(), normalizedPost.author());
		OffsetDateTime now = scraperClock.now();
		Optional<Post> existingPost = findExistingPost(context.source(), normalizedPost);
		Post post;
		boolean created;

		if (existingPost.isPresent()) {
			post = existingPost.get();
			created = false;
			updateExistingPost(post, author, normalizedPost, now);
		} else {
			post = createPost(context.source(), author, normalizedPost, now);
			created = true;
		}

		int mediaCreated = addNewMedia(post, normalizedPost.media());
		int keywordsLinked = linkKeyword(post, context.keyword());
		Post savedPost = postRepository.save(post);
		return new IngestedPostOutcome(savedPost.getId(), created, mediaCreated, keywordsLinked);
	}

	private NormalizedScrapedPost normalizePost(Source source, ScrapedPostDTO scrapedPost) {
		if (scrapedPost == null) {
			throw new IllegalArgumentException("Scraped post must not be null");
		}
		String postUrl = normalizeRequiredUrl(source.getBaseUrl(), scrapedPost.postUrl(), "postUrl");
		String text = nullIfBlank(scrapedPost.text());
		if (text == null && (scrapedPost.media() == null || scrapedPost.media().isEmpty())) {
			throw new IllegalArgumentException("text must not be blank when media is empty");
		}
		OffsetDateTime postDate = scrapedPost.postDate();
		if (postDate == null) {
			throw new IllegalArgumentException("postDate must not be null");
		}
		String textHash = text == null ? null : TextHashGenerator.sha256(text);
		String externalPostId = nullIfBlank(scrapedPost.externalPostId());
		String language = nullIfBlank(scrapedPost.language());
		return new NormalizedScrapedPost(
				externalPostId,
				postUrl,
				postDate,
				text,
				textHash,
				language,
				normalizeAuthor(source, scrapedPost.author()),
				scrapedPost.media(),
				deduplicationKey(externalPostId, postUrl, textHash)
		);
	}

	private NormalizedScrapedAuthor normalizeAuthor(Source source, ScrapedAuthorDTO author) {
		if (author == null) {
			return new NormalizedScrapedAuthor(
					null,
					defaultAuthorUsername(source),
					null,
					normalizeOptionalProfileUrl(source.getBaseUrl())
			);
		}
		String externalId = nullIfBlank(author.externalId());
		String username = firstNonBlank(author.username(), author.displayName(), externalId, defaultAuthorUsername(source));
		String profileUrl = normalizeOptionalProfileUrl(author.profileUrl());
		return new NormalizedScrapedAuthor(externalId, username, nullIfBlank(author.displayName()), profileUrl);
	}

	private Author resolveAuthor(Source source, NormalizedScrapedAuthor scrapedAuthor) {
		Optional<Author> existingAuthor = Optional.empty();
		if (scrapedAuthor.externalId() != null) {
			existingAuthor = authorRepository.findBySourceAndExternalId(source, scrapedAuthor.externalId());
		}
		if (existingAuthor.isEmpty() && scrapedAuthor.profileUrl() != null) {
			existingAuthor = authorRepository.findBySourceAndProfileUrl(source, scrapedAuthor.profileUrl());
		}
		if (existingAuthor.isEmpty()) {
			existingAuthor = authorRepository.findBySourceAndUsername(source, scrapedAuthor.username());
		}

		if (existingAuthor.isPresent()) {
			Author author = existingAuthor.get();
			updateAuthor(author, scrapedAuthor);
			return author;
		}

		Author author = Author.builder()
				.source(source)
				.externalId(scrapedAuthor.externalId())
				.username(scrapedAuthor.username())
				.profileUrl(scrapedAuthor.profileUrl())
				.build();
		return authorRepository.save(author);
	}

	private void updateAuthor(Author author, NormalizedScrapedAuthor scrapedAuthor) {
		if (isBlank(author.getExternalId()) && scrapedAuthor.externalId() != null) {
			author.setExternalId(scrapedAuthor.externalId());
		}
		if (isBlank(author.getProfileUrl()) && scrapedAuthor.profileUrl() != null) {
			author.setProfileUrl(scrapedAuthor.profileUrl());
		}
	}

	private Optional<Post> findExistingPost(Source source, NormalizedScrapedPost scrapedPost) {
		Optional<Post> existingPost = Optional.empty();
		if (scrapedPost.externalPostId() != null) {
			existingPost = postRepository.findBySourceAndExternalPostId(source, scrapedPost.externalPostId());
		}
		if (existingPost.isEmpty()) {
			existingPost = postRepository.findBySourceAndPostUrl(source, scrapedPost.postUrl());
		}
		if (existingPost.isEmpty() && scrapedPost.textHash() != null) {
			existingPost = postRepository.findBySourceAndTextHash(source, scrapedPost.textHash());
		}
		return existingPost;
	}

	private Post createPost(
			Source source,
			Author author,
			NormalizedScrapedPost scrapedPost,
			OffsetDateTime now
	) {
		return Post.builder()
				.source(source)
				.author(author)
				.externalPostId(scrapedPost.externalPostId())
				.postUrl(scrapedPost.postUrl())
				.postDate(scrapedPost.postDate())
				.scrapedAt(now)
				.text(scrapedPost.text())
				.textHash(scrapedPost.textHash())
				.language(scrapedPost.language())
				.media(new ArrayList<>())
				.keywords(new LinkedHashSet<>())
				.build();
	}

	private void updateExistingPost(
			Post post,
			Author author,
			NormalizedScrapedPost scrapedPost,
			OffsetDateTime now
	) {
		post.setScrapedAt(now);
		if (isBlank(post.getExternalPostId()) && scrapedPost.externalPostId() != null) {
			post.setExternalPostId(scrapedPost.externalPostId());
		}
		if (isBlank(post.getText()) && !isBlank(scrapedPost.text())) {
			post.setText(scrapedPost.text());
			post.setTextHash(scrapedPost.textHash());
		}
		if (post.getPostDate() == null && scrapedPost.postDate() != null) {
			post.setPostDate(scrapedPost.postDate());
		}
		if (post.getAuthor() == null || !Objects.equals(post.getAuthor().getId(), author.getId())) {
			post.setAuthor(author);
		}
		if (isBlank(post.getLanguage()) && scrapedPost.language() != null) {
			post.setLanguage(scrapedPost.language());
		}
	}

	private int addNewMedia(Post post, List<ScrapedMediaDTO> scrapedMedia) {
		if (scrapedMedia == null || scrapedMedia.isEmpty()) {
			return 0;
		}

		Set<String> existingUrls = new HashSet<>();
		Set<Integer> usedPositions = new HashSet<>();
		for (PostMedia media : post.getMedia()) {
			existingUrls.add(media.getMediaUrl());
			usedPositions.add(media.getPosition());
		}

		Set<String> seenUrls = new HashSet<>();
		int created = 0;
		int nextPosition = nextAvailablePosition(usedPositions, 0);
		for (ScrapedMediaDTO media : scrapedMedia) {
			if (media == null) {
				continue;
			}
			String mediaUrl = normalizeOptionalUrl(post.getPostUrl(), media.mediaUrl());
			if (mediaUrl == null || !seenUrls.add(mediaUrl) || existingUrls.contains(mediaUrl)) {
				continue;
			}
			int position = resolveMediaPosition(media.position(), usedPositions, nextPosition);
			usedPositions.add(position);
			nextPosition = nextAvailablePosition(usedPositions, position + 1);
			existingUrls.add(mediaUrl);
			post.addMedia(PostMedia.builder()
					.mediaUrl(mediaUrl)
					.mediaType(media.mediaType() == null ? MediaType.UNKNOWN : media.mediaType())
					.position(position)
					.build());
			created++;
		}
		return created;
	}

	private int linkKeyword(Post post, Keyword keyword) {
		if (keyword == null) {
			return 0;
		}
		boolean linkedInCollection = post.getKeywords().stream()
				.anyMatch(postKeyword -> postKeyword.getKeyword().getId().equals(keyword.getId()));
		if (linkedInCollection || (post.getId() != null && postKeywordRepository.existsByPostAndKeyword(post, keyword))) {
			return 0;
		}

		post.getKeywords().add(PostKeyword.builder()
				.post(post)
				.keyword(keyword)
				.matchedText(keyword.getWord())
				.build());
		return 1;
	}

	private int resolveMediaPosition(Integer requestedPosition, Set<Integer> usedPositions, int fallbackPosition) {
		if (requestedPosition != null && requestedPosition >= 0 && !usedPositions.contains(requestedPosition)) {
			return requestedPosition;
		}
		return nextAvailablePosition(usedPositions, fallbackPosition);
	}

	private int nextAvailablePosition(Set<Integer> usedPositions, int start) {
		int position = Math.max(0, start);
		while (usedPositions.contains(position)) {
			position++;
		}
		return position;
	}

	private String normalizeRequiredUrl(String baseUrl, String value, String fieldName) {
		String normalizedUrl = normalizeOptionalUrl(baseUrl, value);
		if (normalizedUrl == null) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return normalizedUrl;
	}

	private String normalizeOptionalProfileUrl(String value) {
		String normalizedUrl = normalizeOptionalUrl(null, value);
		if (normalizedUrl == null) {
			return null;
		}
		return normalizedUrl.endsWith("/") ? normalizedUrl.substring(0, normalizedUrl.length() - 1) : normalizedUrl;
	}

	private String normalizeOptionalUrl(String baseUrl, String value) {
		if (isBlank(value)) {
			return null;
		}
		try {
			String resolved = isBlank(baseUrl) ? UrlNormalizer.normalize(value) : UrlNormalizer.resolve(baseUrl, value);
			return UrlNormalizer.removeTrackingParams(resolved);
		} catch (RuntimeException exception) {
			log.debug("Skipping invalid scraped URL: value={}, error={}", value, exception.getMessage());
			return null;
		}
	}

	private String deduplicationKey(String externalPostId, String postUrl, String textHash) {
		if (externalPostId != null) {
			return "external:" + externalPostId;
		}
		if (postUrl != null) {
			return "url:" + postUrl;
		}
		return "text:" + textHash;
	}

	private String defaultAuthorUsername(Source source) {
		return firstNonBlank(source.getCode(), source.getName(), "unknown");
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (!isBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private String nullIfBlank(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record NormalizedScrapedAuthor(
			String externalId,
			String username,
			String displayName,
			String profileUrl
	) {
	}

	private record NormalizedScrapedPost(
			String externalPostId,
			String postUrl,
			OffsetDateTime postDate,
			String text,
			String textHash,
			String language,
			NormalizedScrapedAuthor author,
			List<ScrapedMediaDTO> media,
			String deduplicationKey
	) {
	}

	private record IngestedPostOutcome(
			UUID postId,
			boolean created,
			int mediaCreated,
			int keywordsLinked
	) {
	}

	private static final class IngestionCounters {

		private final int postsReceived;
		private int postsCreated;
		private int postsUpdated;
		private int postsSkipped;
		private int mediaCreated;
		private int keywordsLinked;
		private final List<String> errors = new ArrayList<>();
		private final List<UUID> savedPostIds = new ArrayList<>();

		private IngestionCounters(int postsReceived) {
			this.postsReceived = postsReceived;
		}

		private void add(IngestedPostOutcome outcome) {
			if (outcome.created()) {
				postsCreated++;
			} else {
				postsUpdated++;
			}
			mediaCreated += outcome.mediaCreated();
			keywordsLinked += outcome.keywordsLinked();
			if (outcome.postId() != null) {
				savedPostIds.add(outcome.postId());
			}
		}

		private void skip(String error) {
			postsSkipped++;
			errors.add(error);
		}

		private ScrapedPostIngestionResult toResult() {
			return new ScrapedPostIngestionResult(
					postsReceived,
					postsCreated,
					postsUpdated,
					postsSkipped,
					mediaCreated,
					keywordsLinked,
					errors,
					savedPostIds
			);
		}
	}
}
