package org.raul.javawebscarper.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequestDTO(
		@NotBlank
		@Size(min = 3, max = 100)
		@Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "must contain only letters, numbers, dot, underscore or hyphen")
		String username,

		@NotBlank
		@Size(min = 12, max = 128)
		String password
) {
}
