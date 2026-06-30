package org.raul.javawebscarper.dto.request.author;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateAuthorRequestDTO(
		@NotNull
		Integer sourceId,

		@Size(max = 255)
		String externalId,

		@NotBlank
		@Size(max = 255)
		String username,

		@URL
		String profileUrl
) {
}
