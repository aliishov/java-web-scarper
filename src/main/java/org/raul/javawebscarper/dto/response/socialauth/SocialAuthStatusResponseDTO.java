package org.raul.javawebscarper.dto.response.socialauth;

import org.raul.javawebscarper.auth.SocialAuthResult;

public record SocialAuthStatusResponseDTO(String platform, String status, String message, boolean connected) {
	public static SocialAuthStatusResponseDTO from(SocialAuthResult result) {
		return new SocialAuthStatusResponseDTO(
				result.platform().id(), result.status().name(), result.message(), result.isValid());
	}
}
