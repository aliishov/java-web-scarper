package org.raul.javawebscarper.scraper.adapter.tiktok;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.browser.BrowserEngineException;
import org.raul.javawebscarper.browser.BrowserPage;
import org.raul.javawebscarper.browser.BrowserSession;
import org.raul.javawebscarper.browser.BrowserSessionFactory;
import org.raul.javawebscarper.browser.BrowserSessionOptions;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TikTokSessionResolver {

	private final BrowserSessionFactory browserSessionFactory;
	private final TikTokProperties properties;
	private final TikTokAuthenticationVerifier authenticationVerifier;

	public TikTokResolvedSession resolve() {
		TikTokAuthenticationMode mode = properties.getAuthenticationMode() == null
				? TikTokAuthenticationMode.AUTO
				: properties.getAuthenticationMode();
		return switch (mode) {
			case AUTHENTICATED -> resolveAuthenticated(false);
			case ANONYMOUS -> resolveAnonymous(false);
			case AUTO -> resolveAuto();
		};
	}

	private TikTokResolvedSession resolveAuto() {
		Optional<Path> configuredStatePath = configuredStatePath();
		if (configuredStatePath.isPresent()) {
			try {
				return resolveAuthenticated(false);
			} catch (TikTokAuthenticationException exception) {
				if (properties.isAuthenticationRequired() || !properties.isAllowAnonymousFallback()) {
					throw exception;
				}
				log.info("TikTok authentication state has expired.");
				return resolveAnonymous(true);
			}
		}
		log.info("TikTok authentication state is missing.");
		return resolveAnonymous(false);
	}

	private TikTokResolvedSession resolveAuthenticated(boolean anonymousFallbackUsed) {
		Path statePath = configuredStatePath()
				.orElseThrow(() -> new TikTokAuthenticationException(
						TikTokAuthenticationStatus.AUTH_STATE_MISSING,
						"TIKTOK_AUTH_STATE_MISSING: TikTok authentication state is not configured. "
								+ "Generate it with the tiktokAuthStateInteractive Gradle task."
				));
		Path validatedPath;
		try {
			validatedPath = TikTokAuthStatePathValidator.validateReadableStatePath(statePath);
		} catch (BrowserEngineException | IllegalArgumentException exception) {
			throw new TikTokAuthenticationException(
					TikTokAuthenticationStatus.AUTH_STATE_MISSING,
					"TIKTOK_AUTH_STATE_MISSING: TikTok authentication state is missing or unreadable. "
							+ "Generate it with the tiktokAuthStateInteractive Gradle task."
			);
		}

		log.info("Creating authenticated TikTok browser session.");
		BrowserSession session = createSession(new BrowserSessionOptions(
				validatedPath,
				properties.getLocale(),
				properties.getTimezoneId(),
				null,
				Map.of()
		));
		TikTokAuthenticationStatus status = verifySession(session);
		if (status == TikTokAuthenticationStatus.AUTHENTICATED) {
			log.info("TikTok authentication state is valid.");
			return new TikTokResolvedSession(session, status, true, anonymousFallbackUsed);
		}
		closeQuietly(session);
		throw new TikTokAuthenticationException(normalizeAuthenticatedFailure(status), failureMessage(normalizeAuthenticatedFailure(status)));
	}

	private TikTokResolvedSession resolveAnonymous(boolean anonymousFallbackUsed) {
		log.info("Creating anonymous TikTok browser session.");
		BrowserSession session = createSession(new BrowserSessionOptions(
				null,
				properties.getLocale(),
				properties.getTimezoneId(),
				null,
				Map.of()
		));
		TikTokAuthenticationStatus status = verifySession(session);
		if (status == TikTokAuthenticationStatus.AUTHENTICATED || status == TikTokAuthenticationStatus.ANONYMOUS_ACCESS) {
			log.info("TikTok anonymous access is available.");
			return new TikTokResolvedSession(session, status, false, anonymousFallbackUsed);
		}
		closeQuietly(session);
		throw new TikTokAuthenticationException(status, failureMessage(status));
	}

	private Optional<Path> configuredStatePath() {
		String authStatePath = properties.getAuthStatePath();
		if (authStatePath == null || authStatePath.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(TikTokAuthStatePathValidator.normalize(authStatePath));
	}

	protected BrowserSession createSession(BrowserSessionOptions options) {
		return browserSessionFactory.createSession(options);
	}

	protected TikTokAuthenticationStatus verifySession(BrowserSession session) {
		BrowserPage page = session.newPage();
		page.navigate(authVerificationUrl());
		page.waitForSelector(TikTokSelectors.BODY, properties.getReadinessTimeoutMs());
		page.waitForTimeout(properties.getActionDelayMs());
		return authenticationVerifier.verify(page);
	}

	private String authVerificationUrl() {
		String url = properties.getAuthVerificationUrl() == null || properties.getAuthVerificationUrl().isBlank()
				? TikTokScraperSupport.BASE_URL
				: properties.getAuthVerificationUrl().trim();
		return url.endsWith("/") ? url : url + "/";
	}

	private TikTokAuthenticationStatus normalizeAuthenticatedFailure(TikTokAuthenticationStatus status) {
		return switch (status) {
			case CAPTCHA_REQUIRED, VERIFICATION_REQUIRED, TWO_FACTOR_REQUIRED, RATE_LIMITED,
					TEMPORARILY_BLOCKED, ACCOUNT_RESTRICTED, CONSENT_REQUIRED -> status;
			default -> TikTokAuthenticationStatus.AUTH_STATE_EXPIRED;
		};
	}

	private String failureMessage(TikTokAuthenticationStatus status) {
		return switch (status) {
			case AUTH_STATE_MISSING ->
					"TIKTOK_AUTH_STATE_MISSING: TikTok authentication state is not configured. Generate it with the tiktokAuthStateInteractive Gradle task.";
			case AUTH_STATE_EXPIRED ->
					"TIKTOK_AUTH_STATE_EXPIRED: TikTok authentication state is missing or expired. Regenerate it with the tiktokAuthStateInteractive Gradle task.";
			case AUTH_REQUIRED ->
					"TIKTOK_LOGIN_REQUIRED: TikTok requires login. Generate an authenticated state with tiktokAuthStateInteractive.";
			case LOGIN_MODAL_BLOCKING ->
					"TIKTOK_LOGIN_MODAL_BLOCKING: TikTok showed a blocking login modal.";
			case CAPTCHA_REQUIRED ->
					"TIKTOK_CAPTCHA_REQUIRED: TikTok requires CAPTCHA. Complete it manually before saving a new state.";
			case VERIFICATION_REQUIRED, TWO_FACTOR_REQUIRED ->
					"TIKTOK_VERIFICATION_REQUIRED: TikTok requires verification. Complete it manually before saving a new state.";
			case RATE_LIMITED ->
					"TIKTOK_RATE_LIMITED: TikTok temporarily rate-limited this session. Retry later.";
			case TEMPORARILY_BLOCKED ->
					"TIKTOK_TEMPORARILY_BLOCKED: TikTok temporarily blocked this session. Retry later.";
			case ACCOUNT_RESTRICTED ->
					"TIKTOK_ACCOUNT_RESTRICTED: The TikTok account/session is restricted.";
			case CONSENT_REQUIRED ->
					"TIKTOK_VERIFICATION_REQUIRED: TikTok requires a consent dialog before scraping can continue.";
			case UNKNOWN ->
					"TIKTOK_AUTH_STATE_EXPIRED: TikTok authentication state could not be verified.";
			case AUTHENTICATED, ANONYMOUS_ACCESS -> "TIKTOK_AUTHENTICATION_OK";
		};
	}

	private void closeQuietly(BrowserSession session) {
		try {
			session.close();
		} catch (RuntimeException exception) {
			log.warn("Failed to close TikTok browser session after authentication resolution failure", exception);
		}
	}
}
