package org.raul.javawebscarper.dto.response.admin;

import org.raul.javawebscarper.model.enumerated.AdminRole;
import java.time.OffsetDateTime;

public record AdminResponseDTO(
		Long id,
		String username,
		AdminRole role,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
