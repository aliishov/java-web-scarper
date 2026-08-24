package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.auth.AuthStateStatus;
import org.raul.javawebscarper.auth.SocialAuthCredentials;
import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthResult;
import org.raul.javawebscarper.auth.SocialAuthStateService;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.auth.handler.SocialLoginHandler;
import org.raul.javawebscarper.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Manages reusable social-network browser sessions without retaining account passwords. */
@Service
@RequiredArgsConstructor
public class SocialAuthManagementService {
	private final SocialAuthProperties properties;
	private final SocialAuthStateValidator validator;
	private final SocialAuthStateService stateService;
	private final List<SocialLoginHandler> loginHandlers;

	public List<SocialAuthResult> statuses() {
		return List.of(SocialPlatform.values()).stream().map(this::statusWithoutBrowser).toList();
	}

	public SocialAuthResult verify(SocialPlatform platform) {
		SocialAuthResult result = validator.validate(platform);
		stateService.update(result);
		return result;
	}

	public SocialAuthResult connect(SocialPlatform platform, SocialAuthCredentials credentials) {
		if (!properties.account(platform).isEnabled()) {
			throw new BadRequestException(platform + " authentication is disabled");
		}
		SocialLoginHandler handler = handlers().get(platform);
		if (handler == null) {
			throw new BadRequestException("No login handler is registered for " + platform);
		}
		SocialAuthResult result = handler.loginAndSaveState(properties.account(platform), credentials);
		stateService.update(result);
		return result;
	}

	public SocialAuthResult disconnect(SocialPlatform platform) {
		try {
			Files.deleteIfExists(properties.statePath(platform));
		} catch (IOException exception) {
			throw new BadRequestException("Authentication state could not be removed");
		}
		SocialAuthResult result = SocialAuthResult.of(platform, AuthStateStatus.MISSING,
				"Authentication state was removed");
		stateService.update(result);
		return result;
	}

	private Map<SocialPlatform, SocialLoginHandler> handlers() {
		Map<SocialPlatform, SocialLoginHandler> handlers = new EnumMap<>(SocialPlatform.class);
		loginHandlers.forEach(handler -> handlers.put(handler.platform(), handler));
		return handlers;
	}

	/** Lists local session state without opening a browser or contacting any social platform. */
	private SocialAuthResult statusWithoutBrowser(SocialPlatform platform) {
		SocialAuthResult cached = stateService.status(platform);
		if (cached.status() != AuthStateStatus.AUTH_REQUIRED) {
			return cached;
		}
		if (!properties.account(platform).isEnabled()) {
			return SocialAuthResult.of(platform, AuthStateStatus.DISABLED, "Authentication is disabled for platform");
		}
		Path statePath = properties.statePath(platform);
		try {
			if (!Files.isRegularFile(statePath) || !Files.isReadable(statePath) || Files.size(statePath) == 0) {
				return SocialAuthResult.of(platform, AuthStateStatus.MISSING, "Authentication state file is missing or unreadable");
			}
		} catch (IOException exception) {
			return SocialAuthResult.of(platform, AuthStateStatus.MISSING, "Authentication state file cannot be inspected");
		}
		// Browser cookies and local storage are persisted in the storage-state file, while
		// SocialAuthStateService is intentionally in-memory. After a backend restart the
		// file is still reusable by scrapers, so expose it as connected without opening a
		// browser. The explicit Verify action remains the source of live validity checks.
		return SocialAuthResult.of(platform, AuthStateStatus.VALID,
				"Saved authentication session is available; click Verify to check it is still active");
	}
}
