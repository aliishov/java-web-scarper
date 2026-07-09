package org.raul.javawebscarper.dto.request.source;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.SourceType;

import java.util.Set;

public record UpdateSourceRequestDTO(
		@NotBlank
		@Size(max = 100)
		String code,

		@NotBlank
		@Size(max = 255)
		String name,

		@NotNull
		SourceType type,

		@NotBlank
		@URL
		String baseUrl,

		Set<Language> supportedLanguages,

		Boolean enabled
) {
}
