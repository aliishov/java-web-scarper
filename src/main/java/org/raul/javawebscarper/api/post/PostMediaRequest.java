package org.raul.javawebscarper.api.post;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.raul.javawebscarper.model.enumerated.MediaType;

public record PostMediaRequest(
		@NotBlank
		@URL
		String mediaUrl,

		@NotNull
		MediaType mediaType,

		@NotNull
		@Min(0)
		Integer position
) {
}
