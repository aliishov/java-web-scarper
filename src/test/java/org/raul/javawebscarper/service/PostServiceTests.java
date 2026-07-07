package org.raul.javawebscarper.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.raul.javawebscarper.dto.request.post.PostKeywordRequestDTO;
import org.raul.javawebscarper.dto.request.post.UpdatePostRequestDTO;
import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.PostKeyword;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.repository.PostRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTests {

	@Mock
	private PostRepository postRepository;

	@Mock
	private SourceService sourceService;

	@Mock
	private AuthorService authorService;

	@Mock
	private KeywordService keywordService;

	private PostService service;
	private Source source;
	private Author author;
	private Keyword keyword;
	private Post post;

	@BeforeEach
	void setUp() {
		service = new PostService(postRepository, sourceService, authorService, keywordService);
		source = Source.builder()
				.id(1)
				.code("baku_ws")
				.name("baku.ws")
				.build();
		author = Author.builder()
				.id(UUID.randomUUID())
				.source(source)
				.username("baku.ws")
				.build();
		keyword = Keyword.builder()
				.id(7)
				.word("Məhkəmə")
				.build();
		post = Post.builder()
				.id(UUID.randomUUID())
				.source(source)
				.author(author)
				.externalPostId("external-1")
				.postUrl("https://baku.ws/post/1")
				.postDate(OffsetDateTime.parse("2026-07-07T11:45:00+04:00"))
				.scrapedAt(OffsetDateTime.parse("2026-07-07T08:00:00Z"))
				.text("Existing post text.")
				.textHash("existing-hash")
				.language("az")
				.media(new ArrayList<>())
				.keywords(new LinkedHashSet<>())
				.build();
		post.getKeywords().add(PostKeyword.builder()
				.id(1)
				.post(post)
				.keyword(keyword)
				.matchedText("Məhkəmə")
				.build());
	}

	@Test
	void updateDoesNotRecreateExistingPostKeywordLink() {
		when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
		when(sourceService.getEntity(source.getId())).thenReturn(source);
		when(authorService.getEntity(author.getId())).thenReturn(author);

		service.update(post.getId(), updateRequest(List.of(new PostKeywordRequestDTO(keyword.getId(), "Məhkəmə"))));

		assertThat(post.getKeywords()).hasSize(1);
		assertThat(post.getKeywords().iterator().next().getKeyword().getId()).isEqualTo(keyword.getId());
		verify(keywordService, never()).getEntity(keyword.getId());
	}

	@Test
	void updateAddsOnlyMissingPostKeywordLinks() {
		Keyword secondKeyword = Keyword.builder()
				.id(8)
				.word("İlham Əliyev")
				.build();
		when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
		when(sourceService.getEntity(source.getId())).thenReturn(source);
		when(authorService.getEntity(author.getId())).thenReturn(author);
		when(keywordService.getEntity(secondKeyword.getId())).thenReturn(secondKeyword);

		service.update(post.getId(), updateRequest(List.of(
				new PostKeywordRequestDTO(keyword.getId(), "Məhkəmə"),
				new PostKeywordRequestDTO(secondKeyword.getId(), "İlham Əliyev")
		)));

		assertThat(post.getKeywords())
				.extracting(postKeyword -> postKeyword.getKeyword().getId())
				.containsExactlyInAnyOrder(keyword.getId(), secondKeyword.getId());
		verify(keywordService).getEntity(secondKeyword.getId());
	}

	private UpdatePostRequestDTO updateRequest(List<PostKeywordRequestDTO> keywords) {
		return new UpdatePostRequestDTO(
				source.getId(),
				author.getId(),
				"external-1",
				"https://baku.ws/post/1",
				OffsetDateTime.parse("2026-07-07T11:45:00+04:00"),
				OffsetDateTime.parse("2026-07-07T08:30:00Z"),
				"Existing post text.",
				"existing-hash",
				"az",
				List.of(),
				keywords
		);
	}
}
