package org.raul.javawebscarper.auth;

import java.util.Locale;

public enum SocialPlatform {
	X, TIKTOK, INSTAGRAM, FACEBOOK, THREADS;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
