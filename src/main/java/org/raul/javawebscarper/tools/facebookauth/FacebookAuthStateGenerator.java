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

	private static final String LOGIN_URL = "https://www.facebook.com/";
	private static final long DEFAULT_TIMEOUT_MS = 180_000;

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
			output.println("Facebook authentication state was created successfully.");
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
		String authStatePath = requiredEnv("FACEBOOK_AUTH_STATE_PATH");
		long timeoutMs = optionalLongEnv("FACEBOOK_AUTH_TIMEOUT_MS", DEFAULT_TIMEOUT_MS);
		return new FacebookAuthStateRequest(Path.of(authStatePath), timeoutMs);
	}

	private void createStorageState(FacebookAuthStateRequest request) {
		createParentDirectory(request.authStatePath());
		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
						.setHeadless(false));
				BrowserContext context = browser.newContext()) {
			Page page = context.newPage();
			page.navigate(LOGIN_URL);
			output.println("Complete Facebook login/checkpoint/2FA manually in the opened browser.");
			output.println("No credentials are read by this task. Waiting for an authenticated Facebook page...");
			if (!waitUntilAuthenticated(page, request.timeoutMs())) {
				throw new IllegalStateException("Facebook authentication did not complete before timeout.");
			}
			context.storageState(new BrowserContext.StorageStateOptions().setPath(request.authStatePath()));
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
			page.waitForTimeout(1_000);
		}
		return false;
	}

	private String requiredEnv(String name) {
		String value = environment.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " environment variable is required.");
		}
		return value.trim();
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

	private void createParentDirectory(Path authStatePath) {
		Path parent = authStatePath.toAbsolutePath().normalize().getParent();
		if (parent == null) {
			return;
		}
		try {
			Files.createDirectories(parent);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Failed to create parent directory for Facebook authentication state file.");
		}
	}

	record FacebookAuthStateRequest(
			Path authStatePath,
			long timeoutMs
	) {
	}
}
