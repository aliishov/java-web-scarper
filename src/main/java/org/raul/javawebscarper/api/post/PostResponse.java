package org.raul.javawebscarper.api.post;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PostResponse(
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
		List<PostMediaResponse> media,
		List<PostKeywordResponse> keywords,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
