package org.raul.javawebscarper.auth;

public record SocialAuthResult(SocialPlatform platform, AuthStateStatus status, String message) {

	public static SocialAuthResult of(SocialPlatform platform, AuthStateStatus status, String message) {
		return new SocialAuthResult(platform, status, message);
	}

	public boolean isValid() {
		return status == AuthStateStatus.VALID || status == AuthStateStatus.DISABLED;
	}
}
