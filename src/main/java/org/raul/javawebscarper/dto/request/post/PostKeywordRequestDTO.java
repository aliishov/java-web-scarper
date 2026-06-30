package org.raul.javawebscarper.dto.request.post;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostKeywordRequestDTO(
		@NotNull
		Integer keywordId,

		@Size(max = 1024)
		String matchedText
) {
}
