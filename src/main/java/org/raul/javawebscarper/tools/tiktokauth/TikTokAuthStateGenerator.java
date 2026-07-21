package org.raul.javawebscarper.tools.tiktokauth;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.scraper.adapter.tiktok.TikTokAuthStatePathValidator;
import org.raul.javawebscarper.scraper.adapter.tiktok.TikTokAuthenticationStatus;
import org.raul.javawebscarper.scraper.adapter.tiktok.TikTokAuthenticationVerifier;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@RequiredArgsConstructor
public class TikTokAuthStateGenerator {

	private static final String DEFAULT_LOGIN_URL = "https://www.tiktok.com/login";
	private static final String DEFAULT_VERIFICATION_URL = "https://www.tiktok.com/";
	private static final String DEFAULT_LOCALE = "en-US";
	private static final String DEFAULT_TIMEZONE_ID = "Asia/Baku";
	private static final long DEFAULT_TIMEOUT_MS = 300_000;
	private static final long STATUS_POLL_INTERVAL_MS = 1_500;

	private final Map<String, String> environment;
	private final PrintStream output;

	public static void main(String[] args) {
		int exitCode = new TikTokAuthStateGenerator(System.getenv(), System.out).run();
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	int run() {
		try {
			TikTokAuthStateCommand command = buildCommand();
			createStorageState(command);
			output.println("TikTok authentication state was saved successfully.");
			return 0;
		} catch (IllegalArgumentException exception) {
			output.println(exception.getMessage());
			return 2;
		} catch (RuntimeException exception) {
			output.println("TikTok authentication state was not created. " + exception.getMessage());
			return 3;
		}
	}

	TikTokAuthStateCommand buildCommand() {
		Path authStatePath = TikTokAuthStatePathValidator.validateOutputPath(TikTokAuthStatePathValidator.normalize(requiredEnv("TIKTOK_AUTH_STATE_PATH")));
		long timeoutMs = optionalLongEnv(
				"TIKTOK_MANUAL_VERIFICATION_TIMEOUT_MS",
				optionalLongEnv("TIKTOK_AUTH_TIMEOUT_MS", DEFAULT_TIMEOUT_MS)
		);
		return new TikTokAuthStateCommand(
				authStatePath,
				optionalEnv("TIKTOK_LOGIN_URL", DEFAULT_LOGIN_URL),
				optionalEnv("TIKTOK_AUTH_VERIFICATION_URL", DEFAULT_VERIFICATION_URL),
				timeoutMs,
				optionalEnv("TIKTOK_LOCALE", DEFAULT_LOCALE),
				optionalEnv("TIKTOK_TIMEZONE_ID", DEFAULT_TIMEZONE_ID)
		);
	}

	protected void createStorageState(TikTokAuthStateCommand command) {
		Path temporaryStatePath = null;
		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
						.setHeadless(false))) {
			temporaryStatePath = createTemporaryStatePath(command.authStatePath());
			try (BrowserContext context = browser.newContext(contextOptions(command))) {
				Page page = context.newPage();
				page.navigate(command.loginUrl());
				output.println("Complete TikTok login in the opened browser.");
				output.println("Complete CAPTCHA, 2FA, QR login, or verification manually if TikTok asks.");
				output.println("No credentials are read by this task. Waiting for an authenticated TikTok page...");
				if (!waitUntilAuthenticated(page, command.manualVerificationTimeoutMs())) {
					throw new TikTokAuthStateException("TikTok authentication did not complete before timeout.");
				}
				context.storageState(new BrowserContext.StorageStateOptions().setPath(temporaryStatePath));
			}
			validateSavedState(browser, command, temporaryStatePath);
			moveVerifiedState(temporaryStatePath, command.authStatePath());
			temporaryStatePath = null;
		} finally {
			deleteInvalidState(temporaryStatePath);
		}
	}

	private Browser.NewContextOptions contextOptions(TikTokAuthStateCommand command) {
		Browser.NewContextOptions options = new Browser.NewContextOptions();
		if (command.locale() != null && !command.locale().isBlank()) {
			options.setLocale(command.locale());
		}
		if (command.timezoneId() != null && !command.timezoneId().isBlank()) {
			options.setTimezoneId(command.timezoneId());
		}
		return options;
	}

	private boolean waitUntilAuthenticated(Page page, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		TikTokAuthenticationStatus previousStatus = TikTokAuthenticationStatus.UNKNOWN;
		while (System.currentTimeMillis() < deadline) {
			TikTokAuthenticationStatus status = TikTokAuthenticationVerifier.inspect(page.url(), page.content());
			if (status == TikTokAuthenticationStatus.AUTHENTICATED) {
				return true;
			}
			if (status != previousStatus) {
				output.println("TikTok authentication status: " + status);
				if (status == TikTokAuthenticationStatus.CAPTCHA_REQUIRED
						|| status == TikTokAuthenticationStatus.VERIFICATION_REQUIRED
						|| status == TikTokAuthenticationStatus.TWO_FACTOR_REQUIRED) {
					output.println("Complete the TikTok verification step in the opened browser.");
				}
				previousStatus = status;
			}
			if (isTerminalUnhealthyStatus(status)) {
				throw new TikTokAuthStateException("TikTok returned " + status + "; cannot create a healthy storage state.");
			}
			page.waitForTimeout(STATUS_POLL_INTERVAL_MS);
		}
		return false;
	}

	private boolean isTerminalUnhealthyStatus(TikTokAuthenticationStatus status) {
		return status == TikTokAuthenticationStatus.RATE_LIMITED
				|| status == TikTokAuthenticationStatus.TEMPORARILY_BLOCKED
				|| status == TikTokAuthenticationStatus.ACCOUNT_RESTRICTED;
	}

	private void validateSavedState(Browser browser, TikTokAuthStateCommand command, Path statePath) {
		try {
			if (!Files.isRegularFile(statePath) || Files.size(statePath) <= 0) {
				throw new TikTokAuthStateException("TikTok authentication state file was not saved correctly.");
			}
		} catch (Exception exception) {
			throw new TikTokAuthStateException("TikTok authentication state file was not saved correctly.");
		}
		Browser.NewContextOptions options = contextOptions(command).setStorageStatePath(statePath);
		try (BrowserContext validationContext = browser.newContext(options)) {
			Page validationPage = validationContext.newPage();
			validationPage.navigate(command.authVerificationUrl());
			validationPage.waitForTimeout(STATUS_POLL_INTERVAL_MS);
			TikTokAuthenticationStatus status = TikTokAuthenticationVerifier.inspect(validationPage.url(), validationPage.content());
			if (status != TikTokAuthenticationStatus.AUTHENTICATED) {
				throw new TikTokAuthStateException("Saved TikTok authentication state is not authenticated. Repeat tiktokAuthStateInteractive.");
			}
		}
	}

	private Path createTemporaryStatePath(Path authStatePath) {
		try {
			Path parent = authStatePath.getParent();
			if (parent == null) {
				throw new TikTokAuthStateException("TIKTOK_AUTH_STATE_PATH must include a parent directory.");
			}
			return Files.createTempFile(parent, "tiktok-storage-state-", ".json");
		} catch (TikTokAuthStateException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new TikTokAuthStateException("Failed to create temporary TikTok authentication state file.");
		}
	}

	private void moveVerifiedState(Path temporaryStatePath, Path targetPath) {
		try {
			Files.move(
					temporaryStatePath,
					targetPath,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
			);
		} catch (Exception atomicMoveException) {
			try {
				Files.move(temporaryStatePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception fallbackException) {
				throw new TikTokAuthStateException("Failed to save verified TikTok authentication state file.");
			}
		}
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
			if (authStatePath != null) {
				Files.deleteIfExists(authStatePath);
			}
		} catch (Exception ignored) {
			// The error path must not expose storage-state content.
		}
	}
}
