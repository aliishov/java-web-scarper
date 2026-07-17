package org.raul.javawebscarper.tools.facebookauth;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.scraper.adapter.facebook.FacebookAuthenticationPageInspector;
import org.raul.javawebscarper.scraper.adapter.facebook.FacebookAuthenticationStatus;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RequiredArgsConstructor
public class FacebookAuthStateGenerator {

	private static final String DEFAULT_LOGIN_URL = "https://www.facebook.com/login";
	private static final String DEFAULT_VERIFICATION_URL = "https://www.facebook.com/";
	private static final String DEFAULT_LOCALE = "en-US";
	private static final String DEFAULT_TIMEZONE_ID = "Asia/Baku";
	private static final long DEFAULT_TIMEOUT_MS = 300_000;
	private static final long STATUS_POLL_INTERVAL_MS = 1_500;

	private final Map<String, String> environment;
	private final PrintStream output;

	public static void main(String[] args) {
		int exitCode = new FacebookAuthStateGenerator(System.getenv(), System.out).run();
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	int run() {
		try {
			FacebookAuthStateRequest request = buildRequest();
			createStorageState(request);
			output.println("Facebook authentication state was saved successfully.");
			return 0;
		} catch (IllegalArgumentException exception) {
			output.println(exception.getMessage());
			return 2;
		} catch (RuntimeException exception) {
			output.println("Facebook authentication state was not created. " + exception.getMessage());
			return 3;
		}
	}

	FacebookAuthStateRequest buildRequest() {
		Path authStatePath = normalizeAuthStatePath(requiredEnv("FACEBOOK_AUTH_STATE_PATH"));
		long timeoutMs = optionalLongEnv(
				"FACEBOOK_MANUAL_VERIFICATION_TIMEOUT_MS",
				optionalLongEnv("FACEBOOK_AUTH_TIMEOUT_MS", DEFAULT_TIMEOUT_MS)
		);
		return new FacebookAuthStateRequest(
				authStatePath,
				optionalEnv("FACEBOOK_LOGIN_URL", DEFAULT_LOGIN_URL),
				optionalEnv("FACEBOOK_AUTH_VERIFICATION_URL", DEFAULT_VERIFICATION_URL),
				timeoutMs,
				optionalEnv("FACEBOOK_LOCALE", DEFAULT_LOCALE),
				optionalEnv("FACEBOOK_TIMEZONE_ID", DEFAULT_TIMEZONE_ID)
		);
	}

	void validateAuthStatePath(Path authStatePath) {
		if (Files.isDirectory(authStatePath)) {
			throw new IllegalArgumentException("FACEBOOK_AUTH_STATE_PATH must point to a file, not a directory.");
		}
		Path parent = authStatePath.getParent();
		if (parent == null) {
			return;
		}
		try {
			Files.createDirectories(parent);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Failed to create parent directory for Facebook authentication state file.");
		}
	}

	private void createStorageState(FacebookAuthStateRequest request) {
		validateAuthStatePath(request.authStatePath());
		boolean stateWriteAttempted = false;
		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
						.setHeadless(false))) {
			try (BrowserContext context = browser.newContext(contextOptions(request))) {
				Page page = context.newPage();
				page.navigate(request.loginUrl());
				output.println("Complete Facebook login in the opened browser.");
				output.println("Complete 2FA, CAPTCHA, or checkpoint manually if Facebook asks.");
				output.println("No credentials are read by this task. Waiting for an authenticated Facebook page...");
				if (!waitUntilAuthenticated(page, request.manualVerificationTimeoutMs())) {
					throw new IllegalStateException("Facebook authentication did not complete before timeout.");
				}
				stateWriteAttempted = true;
				context.storageState(new BrowserContext.StorageStateOptions().setPath(request.authStatePath()));
			}
			validateSavedState(browser, request);
		} catch (RuntimeException exception) {
			if (stateWriteAttempted) {
				deleteInvalidState(request.authStatePath());
			}
			throw exception;
		}
	}

	private Browser.NewContextOptions contextOptions(FacebookAuthStateRequest request) {
		Browser.NewContextOptions options = new Browser.NewContextOptions();
		if (request.locale() != null && !request.locale().isBlank()) {
			options.setLocale(request.locale());
		}
		if (request.timezoneId() != null && !request.timezoneId().isBlank()) {
			options.setTimezoneId(request.timezoneId());
		}
		return options;
	}

	private void validateSavedState(Browser browser, FacebookAuthStateRequest request) {
		try {
			if (!Files.isRegularFile(request.authStatePath()) || Files.size(request.authStatePath()) <= 0) {
				throw new IllegalStateException("Facebook authentication state file was not saved correctly.");
			}
		} catch (Exception exception) {
			deleteInvalidState(request.authStatePath());
			throw new IllegalStateException("Facebook authentication state file was not saved correctly.");
		}
		Browser.NewContextOptions options = contextOptions(request)
				.setStorageStatePath(request.authStatePath());
		try (BrowserContext validationContext = browser.newContext(options)) {
			Page validationPage = validationContext.newPage();
			validationPage.navigate(request.authVerificationUrl());
			validationPage.waitForTimeout(STATUS_POLL_INTERVAL_MS);
			FacebookAuthenticationStatus status = FacebookAuthenticationPageInspector.inspect(
					validationPage.url(),
					validationPage.content()
			);
			if (status != FacebookAuthenticationStatus.AUTHENTICATED) {
				deleteInvalidState(request.authStatePath());
				throw new IllegalStateException("Saved Facebook authentication state is not authenticated. Repeat facebookAuthStateInteractive.");
			}
		}
	}

	private boolean waitUntilAuthenticated(Page page, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		FacebookAuthenticationStatus previousStatus = FacebookAuthenticationStatus.UNKNOWN;
		while (System.currentTimeMillis() < deadline) {
			FacebookAuthenticationStatus status = FacebookAuthenticationPageInspector.inspect(page.url(), page.content());
			if (status == FacebookAuthenticationStatus.AUTHENTICATED) {
				return true;
			}
			if (status != previousStatus) {
				output.println("Facebook authentication status: " + status);
				previousStatus = status;
			}
			if (status == FacebookAuthenticationStatus.RATE_LIMITED
					|| status == FacebookAuthenticationStatus.TEMPORARILY_BLOCKED
					|| status == FacebookAuthenticationStatus.ACCOUNT_RESTRICTED) {
				throw new IllegalStateException("Facebook returned " + status + "; cannot create a healthy storage state.");
			}
			page.waitForTimeout(STATUS_POLL_INTERVAL_MS);
		}
		return false;
	}

	private Path normalizeAuthStatePath(String value) {
		return Path.of(value).toAbsolutePath().normalize();
	}

	private String requiredEnv(String name) {
		String value = environment.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " environment variable is required.");
		}
		return value.trim();
	}

	private String optionalEnv(String name, String fallback) {
		String value = environment.get(name);
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private long optionalLongEnv(String name, long fallback) {
		String value = environment.get(name);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(name + " environment variable must be a number.");
		}
	}

	private void deleteInvalidState(Path authStatePath) {
		try {
			if (authStatePath != null && Files.isRegularFile(authStatePath)) {
				Files.deleteIfExists(authStatePath);
			}
		} catch (Exception ignored) {
			// Do not expose paths or file contents in auth utility output.
		}
	}

	record FacebookAuthStateRequest(
			Path authStatePath,
			String loginUrl,
			String authVerificationUrl,
			long manualVerificationTimeoutMs,
			String locale,
			String timezoneId
	) {
	}
}
