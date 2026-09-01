package org.raul.javawebscarper.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.PostKeyword;
import org.raul.javawebscarper.model.PostMedia;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.model.enumerated.SearchRegion;
import org.raul.javawebscarper.model.enumerated.SourceType;
import org.raul.javawebscarper.repository.AuthorRepository;
import org.raul.javawebscarper.repository.PostKeywordRepository;
import org.raul.javawebscarper.repository.PostRepository;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.support.ScraperClock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapedPostIngestionServiceTests {

	private static final OffsetDateTime POST_DATE = OffsetDateTime.parse("2026-07-07T11:45:00+04:00");
	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-07T08:00:00Z");

	@Mock
	private AuthorRepository authorRepository;

	@Mock
	private PostRepository postRepository;

	@Mock
	private PostKeywordRepository postKeywordRepository;

	private ScrapedPostIngestionService service;
	private Source source;
	private Keyword keyword;
	private ScraperExecutionContext context;

	@BeforeEach
	void setUp() {
		service = new ScrapedPostIngestionService(
				authorRepository,
				postRepository,
				postKeywordRepository,
				new ScraperClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC))
		);
		source = Source.builder()
				.id(1)
				.code("baku_ws")
				.name("baku.ws")
				.baseUrl("https://baku.ws")
				.build();
		keyword = Keyword.builder()
				.id(7)
				.word("Məhkəmə")
				.build();
		ScrapeJob job = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.dateFrom(LocalDate.of(2026, 7, 6))
				.dateTo(LocalDate.of(2026, 7, 7))
				.build();
		context = new ScraperExecutionContext(
				job,
				source,
				keyword,
				OffsetDateTime.parse("2026-07-06T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-07T23:59:59+04:00"),
				5,
				100,
				Map.of()
		);

		lenient().when(authorRepository.save(any(Author.class))).thenAnswer(invocation -> {
			Author author = invocation.getArgument(0);
			if (author.getId() == null) {
				author.setId(UUID.randomUUID());
			}
			return author;
		});
		lenient().when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
			Post post = invocation.getArgument(0);
			if (post.getId() == null) {
				post.setId(UUID.randomUUID());
			}
			return post;
		});
	}

	@Test
	void createsAuthorPostMediaAndKeywordLink() {
		ScrapedPostIngestionResult result = service.ingest(
				context,
				ScraperExecutionResult.success(List.of(scrapedPost("external-1", "https://baku.ws/post/1")))
		);

		assertThat(result.postsReceived()).isEqualTo(1);
		assertThat(result.postsCreated()).isEqualTo(1);
		assertThat(result.postsUpdated()).isZero();
		assertThat(result.mediaCreated()).isEqualTo(2);
		assertThat(result.keywordsLinked()).isEqualTo(1);
		assertThat(result.postsSaved()).isEqualTo(1);
		assertThat(result.savedPostIds()).hasSize(1);

		ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
		verify(postRepository).save(postCaptor.capture());
		Post savedPost = postCaptor.getValue();
		assertThat(savedPost.getAuthor().getUsername()).isEqualTo("baku.ws");
		assertThat(savedPost.getPostUrl()).isEqualTo("https://baku.ws/post/1");
		assertThat(savedPost.getScrapedAt()).isEqualTo(NOW);
		assertThat(savedPost.getMedia()).hasSize(2);
		assertThat(savedPost.getKeywords()).hasSize(1);
	}

	@Test
	void updatesExistingPostByExternalPostIdWithoutDuplicatingMediaOrKeyword() {
		Author author = existingAuthor();
		Post existingPost = existingPost(author);
		existingPost.addMedia(PostMedia.builder()
				.mediaUrl("https://baku.ws/storage/existing.webp")
				.mediaType(MediaType.IMAGE)
				.position(0)
				.build());
		existingPost.getKeywords().add(PostKeyword.builder()
				.post(existingPost)
				.keyword(keyword)
				.matchedText(keyword.getWord())
				.build());

		when(authorRepository.findBySourceAndExternalId(source, "baku.ws")).thenReturn(Optional.of(author));
		when(postRepository.findBySourceAndExternalPostId(source, "external-1")).thenReturn(Optional.of(existingPost));

		ScrapedPostDTO scrapedPost = new ScrapedPostDTO(
				"external-1",
				"https://baku.ws/post/1",
				POST_DATE,
				scrapedAuthor(),
				"Updated scraped text says Ali Mehkeme should not overwrite already good stored text.",
				"az",
				List.of(
						new ScrapedMediaDTO("https://baku.ws/storage/existing.webp", MediaType.IMAGE, 0),
						new ScrapedMediaDTO("https://baku.ws/storage/new.webp", MediaType.IMAGE, 0)
				),
				Map.of()
		);

		ScrapedPostIngestionResult result = service.ingest(context, ScraperExecutionResult.success(List.of(scrapedPost)));

		assertThat(result.postsCreated()).isZero();
		assertThat(result.postsUpdated()).isEqualTo(1);
		assertThat(result.mediaCreated()).isEqualTo(1);
		assertThat(result.keywordsLinked()).isZero();
		assertThat(existingPost.getScrapedAt()).isEqualTo(NOW);
		assertThat(existingPost.getMedia())
				.extracting(PostMedia::getMediaUrl)
				.containsExactly(
						"https://baku.ws/storage/existing.webp",
						"https://baku.ws/storage/new.webp"
				);
		assertThat(existingPost.getMedia())
				.extracting(PostMedia::getPosition)
				.containsExactly(0, 1);
		assertThat(existingPost.getKeywords()).hasSize(1);
		verify(postKeywordRepository, never()).existsByPostAndKeyword(existingPost, keyword);
	}

	@Test
	void matchesKeywordWhenAzerbaijaniLettersDiffer() {
		keyword.setWord("Ali Mehkeme");
		ScrapedPostDTO scrapedPost = new ScrapedPostDTO(
				"external-1",
				"https://baku.ws/post/1",
				POST_DATE,
				scrapedAuthor(),
				"Azərbaycan Ali Məhkəmə Plenumu yeni qərar qəbul edib.",
				"az",
				List.of(),
				Map.of()
		);

		ScrapedPostIngestionResult result = service.ingest(
				context,
				ScraperExecutionResult.success(List.of(scrapedPost))
		);

		assertThat(result.postsCreated()).isEqualTo(1);
		assertThat(result.postsSkipped()).isZero();
		ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
		verify(postRepository).save(postCaptor.capture());
		assertThat(postCaptor.getValue().getKeywords())
				.extracting(PostKeyword::getMatchedText)
				.containsExactly("Ali Mehkeme");
	}

	@Test
	void skipsPostWhenTextDoesNotMatchKeyword() {
		ScrapedPostDTO unrelatedPost = new ScrapedPostDTO(
				"external-1",
				"https://threads.com/@krpena/post/1",
				POST_DATE,
				new ScrapedAuthorDTO("krpena", "krpena", "Krpena", "https://threads.com/@krpena", null),
				"Zavrsila sam i uslikala je pre 5 min.",
				"bs",
				List.of(new ScrapedMediaDTO("https://threads.com/image.jpg", MediaType.IMAGE, 0)),
				Map.of()
		);

		ScrapedPostIngestionResult result = service.ingest(
				context,
				ScraperExecutionResult.success(List.of(unrelatedPost))
		);

		assertThat(result.postsCreated()).isZero();
		assertThat(result.postsSkipped()).isEqualTo(1);
		assertThat(result.errors()).hasSize(1);
		verify(authorRepository, never()).save(any(Author.class));
		verify(postRepository, never()).save(any(Post.class));
	}

	@Test
	void skipsSocialPostWhenSelectedRegionDoesNotMatch() {
		source.setType(SourceType.SOCIAL);
		keyword.setWord("Ali Mehkeme");
		context = context(SearchRegion.AZ);
		ScrapedPostDTO foreignPost = new ScrapedPostDTO(
				"external-1",
				"https://threads.com/@court/post/1",
				POST_DATE,
				new ScrapedAuthorDTO("court", "court", "Court", "https://threads.com/@court", null),
				"Ali Mehkeme haqqında xarici analitik paylaşım.",
				"tr",
				List.of(),
				Map.of()
		);

		ScrapedPostIngestionResult result = service.ingest(
				context,
				ScraperExecutionResult.success(List.of(foreignPost))
		);

		assertThat(result.postsCreated()).isZero();
		assertThat(result.postsSkipped()).isEqualTo(1);
		verify(postRepository, never()).save(any(Post.class));
	}

	@Test
	void savesSocialPostWhenSelectedRegionMatchesLanguageHint() {
		source.setType(SourceType.SOCIAL);
		keyword.setWord("Ali Mehkeme");
		context = context(SearchRegion.AZ);
		ScrapedPostDTO localPost = new ScrapedPostDTO(
				"external-1",
				"https://threads.com/@court/post/1",
				POST_DATE,
				new ScrapedAuthorDTO("court", "court", "Court", "https://threads.com/@court", null),
				"Ali Məhkəmə yeni qərar qəbul edib.",
				"az",
				List.of(),
				Map.of()
		);

		ScrapedPostIngestionResult result = service.ingest(
				context,
				ScraperExecutionResult.success(List.of(localPost))
		);

		assertThat(result.postsCreated()).isEqualTo(1);
		assertThat(result.postsSkipped()).isZero();
		verify(postRepository).save(any(Post.class));
	}

	@Test
	void skipsDuplicateScrapedPostsInsideSameResult() {
		ScrapedPostDTO first = scrapedPost("external-1", "https://baku.ws/post/1");
		ScrapedPostDTO duplicate = scrapedPost("external-1", "https://baku.ws/post/1?utm_source=test");

		ScrapedPostIngestionResult result = service.ingest(
				context,
				ScraperExecutionResult.success(List.of(first, duplicate))
		);

		assertThat(result.postsCreated()).isEqualTo(1);
		assertThat(result.postsSkipped()).isEqualTo(1);
		assertThat(result.errors()).hasSize(1);
		verify(postRepository).save(any(Post.class));
	}

	@Test
	void skipsMediaOnlyPostBecauseKeywordMatchCannotBeVerified() {
		ScrapedPostDTO mediaOnlyPost = new ScrapedPostDTO(
				"status-123",
				"https://x.com/example/status/123",
				POST_DATE,
				new ScrapedAuthorDTO("x-user", "x-user", "X User", "https://x.com/x-user", null),
				null,
				"az",
				List.of(new ScrapedMediaDTO("https://pbs.twimg.com/media/example.jpg", MediaType.IMAGE, 0)),
				Map.of()
		);

		ScrapedPostIngestionResult result = service.ingest(
				context,
				ScraperExecutionResult.success(List.of(mediaOnlyPost))
		);

		assertThat(result.postsCreated()).isZero();
		assertThat(result.postsSkipped()).isEqualTo(1);
		verify(postRepository, never()).save(any(Post.class));
	}

	private ScrapedPostDTO scrapedPost(String externalPostId, String postUrl) {
		return new ScrapedPostDTO(
				externalPostId,
				postUrl,
				POST_DATE,
				scrapedAuthor(),
				"Real scraped article text says Məhkəmə decision is important enough to be persisted.",
				"az",
				List.of(
						new ScrapedMediaDTO("https://baku.ws/storage/existing.webp", MediaType.IMAGE, 0),
						new ScrapedMediaDTO("https://baku.ws/storage/video.mp4", MediaType.VIDEO, 1)
				),
				Map.of()
		);
	}

	private ScraperExecutionContext context(SearchRegion searchRegion) {
		return new ScraperExecutionContext(
				context.job(),
				source,
				keyword,
				context.dateFrom(),
				context.dateTo(),
				context.maxPages(),
				context.maxPosts(),
				searchRegion,
				context.metadata()
		);
	}

	private ScrapedAuthorDTO scrapedAuthor() {
		return new ScrapedAuthorDTO(
				"baku.ws",
				"baku.ws",
				"Baku.ws",
				"https://baku.ws/",
				null
		);
	}

	private Author existingAuthor() {
		return Author.builder()
				.id(UUID.randomUUID())
				.source(source)
				.externalId("baku.ws")
				.username("baku.ws")
				.profileUrl("https://baku.ws")
				.build();
	}

	private Post existingPost(Author author) {
		return Post.builder()
				.id(UUID.randomUUID())
				.source(source)
				.author(author)
				.externalPostId("external-1")
				.postUrl("https://baku.ws/post/1")
				.postDate(POST_DATE)
				.scrapedAt(OffsetDateTime.parse("2026-07-07T07:00:00Z"))
				.text("Existing stored text is already good.")
				.textHash("existing-hash")
				.language("az")
				.media(new ArrayList<>())
				.keywords(new LinkedHashSet<>())
				.build();
	}
}
