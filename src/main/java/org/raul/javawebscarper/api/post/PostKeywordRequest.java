package org.raul.javawebscarper.api.post;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostKeywordRequest(
		@NotNull
		Integer keywordId,

		@Size(max = 1024)
		String matchedText
) {
}
