package org.raul.javawebscarper.tools.instagramauth;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.scraper.adapter.instagram.InstagramAuthenticationPageInspector;
import org.raul.javawebscarper.scraper.adapter.instagram.InstagramAuthenticationStatus;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
public class InstagramAuthStateGenerator {

	private static final String DEFAULT_LOGIN_URL = "https://www.instagram.com/accounts/login/";
	private static final String DEFAULT_VERIFICATION_URL = "https://www.instagram.com/";
	private static final String DEFAULT_LOCALE = "en-US";
	private static final String DEFAULT_TIMEZONE_ID = "Asia/Baku";
	private static final long DEFAULT_TIMEOUT_MS = 300_000;
	private static final long STATUS_POLL_INTERVAL_MS = 1_500;

	private final Map<String, String> environment;
	private final PrintStream output;

	public static void main(String[] args) {
		int exitCode = new InstagramAuthStateGenerator(System.getenv(), System.out).run();
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	int run() {
		try {
			InstagramAuthStateRequest request = buildRequest();
			createStorageState(request);
			output.println("Instagram authentication state was saved successfully.");
			return 0;
		} catch (IllegalArgumentException exception) {
			output.println(exception.getMessage());
			return 2;
		} catch (RuntimeException exception) {
			output.println("Instagram authentication state was not created. " + exception.getMessage());
			return 3;
		}
	}

	InstagramAuthStateRequest buildRequest() {
		Path authStatePath = normalizeAuthStatePath(requiredEnv("INSTAGRAM_AUTH_STATE_PATH"));
		long timeoutMs = optionalLongEnv(
				"INSTAGRAM_MANUAL_VERIFICATION_TIMEOUT_MS",
				optionalLongEnv("INSTAGRAM_AUTH_TIMEOUT_MS", DEFAULT_TIMEOUT_MS)
		);
		return new InstagramAuthStateRequest(
				authStatePath,
				optionalEnv("INSTAGRAM_LOGIN_URL", DEFAULT_LOGIN_URL),
				optionalEnv("INSTAGRAM_AUTH_VERIFICATION_URL", DEFAULT_VERIFICATION_URL),
				timeoutMs,
				optionalEnv("INSTAGRAM_LOCALE", DEFAULT_LOCALE),
				optionalEnv("INSTAGRAM_TIMEZONE_ID", DEFAULT_TIMEZONE_ID)
		);
	}

	void validateAuthStatePath(Path authStatePath) {
		Path normalizedPath = authStatePath.toAbsolutePath().normalize();
		if (!normalizedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
			throw new IllegalArgumentException("INSTAGRAM_AUTH_STATE_PATH must point to a JSON file.");
		}
		if (isForbiddenRepositoryPath(normalizedPath)) {
			throw new IllegalArgumentException("INSTAGRAM_AUTH_STATE_PATH must not point to source, config, docs, or Git metadata files.");
		}
		if (Files.isDirectory(authStatePath)) {
			throw new IllegalArgumentException("INSTAGRAM_AUTH_STATE_PATH must point to a file, not a directory.");
		}
		Path parent = normalizedPath.getParent();
		if (parent == null) {
			return;
		}
		try {
			Files.createDirectories(parent);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Failed to create parent directory for Instagram authentication state file.");
		}
	}

	private void createStorageState(InstagramAuthStateRequest request) {
		validateAuthStatePath(request.authStatePath());
		Path temporaryStatePath = null;
		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
						.setHeadless(false))) {
			temporaryStatePath = createTemporaryStatePath(request.authStatePath());
			try (BrowserContext context = browser.newContext(contextOptions(request))) {
				Page page = context.newPage();
				page.navigate(request.loginUrl());
				output.println("Complete Instagram login in the opened browser.");
				output.println("Complete 2FA, challenge, or consent steps manually if Instagram asks.");
				output.println("No credentials are read by this task. Waiting for an authenticated Instagram page...");
				if (!waitUntilAuthenticated(page, request.manualVerificationTimeoutMs())) {
					throw new IllegalStateException("Instagram authentication did not complete before timeout.");
				}
				context.storageState(new BrowserContext.StorageStateOptions().setPath(temporaryStatePath));
			}
			validateSavedState(browser, request, temporaryStatePath);
			moveVerifiedState(temporaryStatePath, request.authStatePath());
			temporaryStatePath = null;
		} catch (RuntimeException exception) {
			throw exception;
		} finally {
			deleteInvalidState(temporaryStatePath);
		}
	}

	private Browser.NewContextOptions contextOptions(InstagramAuthStateRequest request) {
		Browser.NewContextOptions options = new Browser.NewContextOptions();
		if (request.locale() != null && !request.locale().isBlank()) {
			options.setLocale(request.locale());
		}
		if (request.timezoneId() != null && !request.timezoneId().isBlank()) {
			options.setTimezoneId(request.timezoneId());
		}
		return options;
	}

	private void validateSavedState(Browser browser, InstagramAuthStateRequest request, Path statePath) {
		try {
			if (!Files.isRegularFile(statePath) || Files.size(statePath) <= 0) {
				throw new IllegalStateException("Instagram authentication state file was not saved correctly.");
			}
		} catch (Exception exception) {
			throw new IllegalStateException("Instagram authentication state file was not saved correctly.");
		}
		Browser.NewContextOptions options = contextOptions(request)
				.setStorageStatePath(statePath);
		try (BrowserContext validationContext = browser.newContext(options)) {
			Page validationPage = validationContext.newPage();
			validationPage.navigate(request.authVerificationUrl());
			validationPage.waitForTimeout(STATUS_POLL_INTERVAL_MS);
			InstagramAuthenticationStatus status = InstagramAuthenticationPageInspector.inspect(
					validationPage.url(),
					validationPage.content()
			);
			if (status != InstagramAuthenticationStatus.AUTHENTICATED) {
				throw new IllegalStateException("Saved Instagram authentication state is not authenticated. Repeat instagramAuthStateInteractive.");
			}
		}
	}

	private boolean waitUntilAuthenticated(Page page, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		InstagramAuthenticationStatus previousStatus = InstagramAuthenticationStatus.UNKNOWN;
		while (System.currentTimeMillis() < deadline) {
			InstagramAuthenticationStatus status = InstagramAuthenticationPageInspector.inspect(page.url(), page.content());
			if (status == InstagramAuthenticationStatus.AUTHENTICATED) {
				return true;
			}
			if (status != previousStatus) {
				output.println("Instagram authentication status: " + status);
				previousStatus = status;
			}
			if (status == InstagramAuthenticationStatus.RATE_LIMITED
					|| status == InstagramAuthenticationStatus.TEMPORARILY_BLOCKED
					|| status == InstagramAuthenticationStatus.ACCOUNT_RESTRICTED) {
				throw new IllegalStateException("Instagram returned " + status + "; cannot create a healthy storage state.");
			}
			page.waitForTimeout(STATUS_POLL_INTERVAL_MS);
		}
		return false;
	}

	private Path normalizeAuthStatePath(String value) {
		return Path.of(value).toAbsolutePath().normalize();
	}

	private boolean isForbiddenRepositoryPath(Path authStatePath) {
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		if (!authStatePath.startsWith(workingDirectory)) {
			return false;
		}
		Path relative = workingDirectory.relativize(authStatePath);
		if (relative.getNameCount() == 0) {
			return true;
		}
		String firstSegment = relative.getName(0).toString().toLowerCase(Locale.ROOT);
		if (firstSegment.equals("src")
				|| firstSegment.equals(".git")
				|| firstSegment.equals("gradle")
				|| firstSegment.equals("docs")
				|| firstSegment.equals(".idea")) {
			return true;
		}
		String fileName = authStatePath.getFileName().toString().toLowerCase(Locale.ROOT);
		return fileName.equals("build.gradle")
				|| fileName.equals("settings.gradle")
				|| fileName.equals("application.yaml")
				|| fileName.equals("application.yml")
				|| fileName.equals("compose.yaml");
	}

	private Path createTemporaryStatePath(Path authStatePath) {
		try {
			Path parent = authStatePath.getParent();
			if (parent == null) {
				parent = Path.of("").toAbsolutePath().normalize();
			}
			return Files.createTempFile(parent, "instagram-storage-state-", ".json");
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to create temporary Instagram authentication state file.");
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
				throw new IllegalStateException("Failed to save verified Instagram authentication state file.");
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
			if (authStatePath != null && Files.isRegularFile(authStatePath)) {
				Files.deleteIfExists(authStatePath);
			}
		} catch (Exception ignored) {
			// Do not expose paths or file contents in auth utility output.
		}
	}

	record InstagramAuthStateRequest(
			Path authStatePath,
			String loginUrl,
			String authVerificationUrl,
			long manualVerificationTimeoutMs,
			String locale,
			String timezoneId
	) {
	}
}
