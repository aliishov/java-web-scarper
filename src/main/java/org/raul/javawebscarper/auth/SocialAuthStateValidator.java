package org.raul.javawebscarper.auth;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.browser.BrowserPage;
import org.raul.javawebscarper.browser.BrowserSession;
import org.raul.javawebscarper.browser.BrowserSessionFactory;
import org.raul.javawebscarper.browser.BrowserSessionOptions;
import org.raul.javawebscarper.scraper.adapter.facebook.FacebookAuthenticationPageInspector;
import org.raul.javawebscarper.scraper.adapter.facebook.FacebookAuthenticationStatus;
import org.raul.javawebscarper.scraper.adapter.instagram.InstagramAuthenticationPageInspector;
import org.raul.javawebscarper.scraper.adapter.instagram.InstagramAuthenticationStatus;
import org.raul.javawebscarper.scraper.adapter.tiktok.TikTokAuthenticationStatus;
import org.raul.javawebscarper.scraper.adapter.tiktok.TikTokAuthenticationVerifier;
import org.raul.javawebscarper.scraper.adapter.xcom.XAuthenticationPageInspector;
import org.raul.javawebscarper.scraper.adapter.xcom.XAuthenticationStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SocialAuthStateValidator {
	private final BrowserSessionFactory sessionFactory;
	private final SocialAuthProperties properties;

	public SocialAuthResult validate(SocialPlatform platform) {
		return validate(platform, properties.statePath(platform));
	}

	/** Validates a candidate state before it replaces the active platform session. */
	public SocialAuthResult validate(SocialPlatform platform, Path path) {
		SocialAuthAccountProperties account = properties.account(platform);
		if (!account.isEnabled()) {
			return result(platform, AuthStateStatus.DISABLED, "Authentication bootstrap is disabled for platform");
		}
		try {
			if (!Files.isRegularFile(path) || !Files.isReadable(path) || Files.size(path) == 0) {
				return result(platform, AuthStateStatus.MISSING, "Authentication state file is missing or unreadable");
			}
		} catch (Exception exception) {
			return result(platform, AuthStateStatus.MISSING, "Authentication state file cannot be inspected");
		}
		try (BrowserSession session = sessionFactory.createSession(BrowserSessionOptions.withStorageState(path))) {
			BrowserPage page = session.getPage();
			page.navigate(account.getValidationUrl());
			page.waitForTimeout(1_500);
			return inspectCurrentPage(platform, page.url(), page.content());
		} catch (RuntimeException exception) {
			return result(platform, AuthStateStatus.EXPIRED, "Authentication state validation failed");
		}
	}

	/**
	 * Inspects the already-open interactive login page without creating another
	 * browser session. This keeps the administrator's login window stable while
	 * its authentication state is being monitored.
	 */
	public SocialAuthResult inspectCurrentPage(SocialPlatform platform, String url, String html) {
		return switch (platform) {
			case X -> fromX(XAuthenticationPageInspector.inspect(url, html));
			case TIKTOK -> fromTikTok(TikTokAuthenticationVerifier.inspect(url, html));
			case INSTAGRAM -> fromInstagram(InstagramAuthenticationPageInspector.inspect(url, html));
			case FACEBOOK -> fromFacebook(FacebookAuthenticationPageInspector.inspect(url, html));
			case THREADS -> fromThreads(url, html);
		};
	}

	private SocialAuthResult fromX(XAuthenticationStatus status) {
		return result(SocialPlatform.X, switch (status) {
			case AUTHENTICATED -> AuthStateStatus.VALID;
			case CHALLENGE_REQUIRED -> AuthStateStatus.CHALLENGE_REQUIRED;
			case AUTH_REQUIRED -> AuthStateStatus.AUTH_REQUIRED;
			default -> AuthStateStatus.EXPIRED;
		}, "X authentication status: " + status);
	}

	private SocialAuthResult fromTikTok(TikTokAuthenticationStatus status) {
		return result(SocialPlatform.TIKTOK, switch (status) {
			case AUTHENTICATED -> AuthStateStatus.VALID;
			case CAPTCHA_REQUIRED, VERIFICATION_REQUIRED, TWO_FACTOR_REQUIRED -> AuthStateStatus.CHALLENGE_REQUIRED;
			case AUTH_REQUIRED, LOGIN_MODAL_BLOCKING -> AuthStateStatus.AUTH_REQUIRED;
			default -> AuthStateStatus.EXPIRED;
		}, "TikTok authentication status: " + status);
	}

	private SocialAuthResult fromInstagram(InstagramAuthenticationStatus status) {
		return result(SocialPlatform.INSTAGRAM, switch (status) {
			case AUTHENTICATED -> AuthStateStatus.VALID;
			case CHALLENGE_REQUIRED, TWO_FACTOR_REQUIRED, SUSPICIOUS_LOGIN -> AuthStateStatus.CHALLENGE_REQUIRED;
			case AUTH_REQUIRED -> AuthStateStatus.AUTH_REQUIRED;
			default -> AuthStateStatus.EXPIRED;
		}, "Instagram authentication status: " + status);
	}

	private SocialAuthResult fromFacebook(FacebookAuthenticationStatus status) {
		return result(SocialPlatform.FACEBOOK, switch (status) {
			case AUTHENTICATED -> AuthStateStatus.VALID;
			case CHECKPOINT_REQUIRED, CHALLENGE_REQUIRED, TWO_FACTOR_REQUIRED -> AuthStateStatus.CHALLENGE_REQUIRED;
			case AUTH_REQUIRED -> AuthStateStatus.AUTH_REQUIRED;
			default -> AuthStateStatus.EXPIRED;
		}, "Facebook authentication status: " + status);
	}

	private SocialAuthResult fromThreads(String url, String html) {
		String target = (url + " " + html).toLowerCase(Locale.ROOT);
		if (target.contains("/login") || target.contains("type=\"password\"")
				|| target.contains("log in with instagram") || target.contains("checkpoint")) {
			return result(SocialPlatform.THREADS,
					target.contains("checkpoint") ? AuthStateStatus.CHALLENGE_REQUIRED : AuthStateStatus.AUTH_REQUIRED,
					"Threads session is not authenticated");
		}
		return result(SocialPlatform.THREADS, AuthStateStatus.VALID, "Threads authentication state is valid");
	}

	private SocialAuthResult result(SocialPlatform platform, AuthStateStatus status, String message) {
		return SocialAuthResult.of(platform, status, message);
	}
}
