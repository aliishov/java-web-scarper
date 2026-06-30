package org.raul.javawebscarper.dto.response.post;

import java.time.OffsetDateTime;

public record PostKeywordResponseDTO(
		Integer id,
		Integer keywordId,
		String keywordWord,
		String matchedText,
		OffsetDateTime createdAt
) {
}
