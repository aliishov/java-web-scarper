package org.raul.javawebscarper.dto.request.keyword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.raul.javawebscarper.model.enumerated.Language;

public record UpdateKeywordRequestDTO(
		@NotBlank
		@Size(max = 255)
		String word,

		Language language,

		Boolean enabled
) {
}
