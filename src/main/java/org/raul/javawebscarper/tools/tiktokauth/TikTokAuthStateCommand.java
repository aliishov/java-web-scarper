package org.raul.javawebscarper.tools.tiktokauth;

import java.nio.file.Path;

public record TikTokAuthStateCommand(
		Path authStatePath,
		String loginUrl,
		String authVerificationUrl,
		long manualVerificationTimeoutMs,
		String locale,
		String timezoneId
) {
}
