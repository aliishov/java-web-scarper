package org.raul.javawebscarper.api.keyword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KeywordRequest(
		@NotBlank
		@Size(max = 255)
		String word,

		Boolean enabled
) {
}
