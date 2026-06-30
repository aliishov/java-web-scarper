package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.dto.response.post.PostKeywordResponseDTO;
import org.raul.javawebscarper.dto.response.post.PostMediaResponseDTO;
import org.raul.javawebscarper.dto.request.post.CreatePostRequestDTO;
import org.raul.javawebscarper.dto.request.post.UpdatePostRequestDTO;
import org.raul.javawebscarper.dto.response.post.PostResponseDTO;
import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.PostKeyword;
import org.raul.javawebscarper.model.PostMedia;
import org.raul.javawebscarper.model.Source;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;

@Component
public class PostMapper {

	public Post toEntity(CreatePostRequestDTO request, Source source, Author author) {
		return Post.builder()
				.source(source)
				.author(author)
				.externalPostId(nullIfBlank(request.externalPostId()))
				.postUrl(request.postUrl().trim())
				.postDate(request.postDate())
				.scrapedAt(request.scrapedAt() == null ? OffsetDateTime.now() : request.scrapedAt())
				.text(request.text().trim())
				.textHash(request.textHash().trim())
				.language(nullIfBlank(request.language()))
				.media(new ArrayList<>())
				.keywords(new LinkedHashSet<>())
				.build();
	}

	public void updateEntity(Post post, UpdatePostRequestDTO request, Source source, Author author) {
		post.setSource(source);
		post.setAuthor(author);
		post.setExternalPostId(nullIfBlank(request.externalPostId()));
		post.setPostUrl(request.postUrl().trim());
		post.setPostDate(request.postDate());
		post.setScrapedAt(request.scrapedAt() == null ? post.getScrapedAt() : request.scrapedAt());
		post.setText(request.text().trim());
		post.setTextHash(request.textHash().trim());
		post.setLanguage(nullIfBlank(request.language()));
	}

	public PostResponseDTO toResponse(Post post) {
		return new PostResponseDTO(
				post.getId(),
				post.getSource().getId(),
				post.getSource().getCode(),
				post.getAuthor().getId(),
				post.getAuthor().getUsername(),
				post.getExternalPostId(),
				post.getPostUrl(),
				post.getPostDate(),
				post.getScrapedAt(),
				post.getText(),
				post.getTextHash(),
				post.getLanguage(),
				post.getMedia().stream()
						.sorted(Comparator.comparing(PostMedia::getPosition))
						.map(this::toMediaResponse)
						.toList(),
				post.getKeywords().stream()
						.sorted(Comparator.comparing(postKeyword -> postKeyword.getKeyword().getWord()))
						.map(this::toKeywordResponse)
						.toList(),
				post.getCreatedAt(),
				post.getUpdatedAt()
		);
	}

	private PostMediaResponseDTO toMediaResponse(PostMedia postMedia) {
		return new PostMediaResponseDTO(
				postMedia.getId(),
				postMedia.getMediaUrl(),
				postMedia.getMediaType(),
				postMedia.getPosition(),
				postMedia.getCreatedAt()
		);
	}

	private PostKeywordResponseDTO toKeywordResponse(PostKeyword postKeyword) {
		return new PostKeywordResponseDTO(
				postKeyword.getId(),
				postKeyword.getKeyword().getId(),
				postKeyword.getKeyword().getWord(),
				postKeyword.getMatchedText(),
				postKeyword.getCreatedAt()
		);
	}

	private String nullIfBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
