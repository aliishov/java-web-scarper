package org.raul.javawebscarper.dto.request.keyword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKeywordRequestDTO(
		@NotBlank
		@Size(max = 255)
		String word,

		Boolean enabled
) {
}
