package org.raul.javawebscarper.auth;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
public class SocialAuthStateService {
	private final Map<SocialPlatform, SocialAuthResult> states = new EnumMap<>(SocialPlatform.class);

	public synchronized void update(SocialAuthResult result) {
		states.put(result.platform(), result);
	}

	public synchronized SocialAuthResult status(SocialPlatform platform) {
		return states.getOrDefault(platform,
				SocialAuthResult.of(platform, AuthStateStatus.AUTH_REQUIRED, "Authentication state has not been validated"));
	}

	public synchronized Map<SocialPlatform, SocialAuthResult> snapshot() {
		return Map.copyOf(states);
	}
}
