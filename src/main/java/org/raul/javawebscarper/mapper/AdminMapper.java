package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.dto.response.admin.AdminResponseDTO;
import org.raul.javawebscarper.model.Admin;

public final class AdminMapper {
	private AdminMapper() {
	}

	public static AdminResponseDTO toResponse(Admin admin) {
		return new AdminResponseDTO(admin.getId(), admin.getUsername(), admin.getRole(),
				admin.getCreatedAt(), admin.getUpdatedAt());
	}
}
