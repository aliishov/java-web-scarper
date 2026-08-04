package org.raul.javawebscarper.dto.request.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.raul.javawebscarper.model.enumerated.AdminRole;

public record UpdateAdminRequestDTO(
		@Size(min = 3, max = 100)
		@Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "must contain only letters, numbers, dot, underscore or hyphen")
		String username,
		@Size(min = 12, max = 128) String password,
		AdminRole role
) {
}
