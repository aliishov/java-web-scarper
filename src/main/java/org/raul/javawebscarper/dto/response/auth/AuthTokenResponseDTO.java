package org.raul.javawebscarper.dto.response.auth;

public record AuthTokenResponseDTO(
		String accessToken,
		String refreshToken
) {
}
