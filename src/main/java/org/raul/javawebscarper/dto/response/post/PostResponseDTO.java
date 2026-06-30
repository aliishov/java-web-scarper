package org.raul.javawebscarper.dto.response.post;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PostResponseDTO(
		UUID id,
		Integer sourceId,
		String sourceCode,
		UUID authorId,
		String authorUsername,
		String externalPostId,
		String postUrl,
		OffsetDateTime postDate,
		OffsetDateTime scrapedAt,
		String text,
		String textHash,
		String language,
		List<PostMediaResponseDTO> media,
		List<PostKeywordResponseDTO> keywords,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
