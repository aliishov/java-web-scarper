package org.raul.javawebscarper.api.post;

import java.time.OffsetDateTime;

public record PostKeywordResponse(
		Integer id,
		Integer keywordId,
		String keywordWord,
		String matchedText,
		OffsetDateTime createdAt
) {
}
