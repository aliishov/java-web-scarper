package org.raul.javawebscarper.tools.xauth;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.scraper.adapter.xcom.XAuthenticationPageInspector;
import org.raul.javawebscarper.scraper.adapter.xcom.XAuthenticationStatus;

import java.io.Console;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

@RequiredArgsConstructor
public class XAuthStateGenerator {

	private static final String LOGIN_URL = "https://x.com/i/flow/login";
	private static final long DEFAULT_TIMEOUT_MS = 60_000;
	private static final String USERNAME_INPUT = "input[autocomplete='username'], input[name='text']";
	private static final String PASSWORD_INPUT = "input[type='password'], input[name='password']";
	private static final String NEXT_BUTTON = "button:has-text('Next'), div[role='button']:has-text('Next'), "
			+ "button:has-text('Далее'), div[role='button']:has-text('Далее')";
	private static final String LOGIN_BUTTON = "button:has-text('Log in'), div[role='button']:has-text('Log in'), "
			+ "button:has-text('Войти'), div[role='button']:has-text('Войти')";

	private final Map<String, String> environment;
	private final Console console;
	private final PrintStream output;

	public static void main(String[] args) {
		int exitCode = new XAuthStateGenerator(System.getenv(), System.console(), System.out).run(args);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	int run(String[] args) {
		XAuthStateRequest request = null;
		try {
			request = buildRequest(args);
			createStorageState(request);
			output.println("X authentication state was created successfully.");
			return 0;
		} catch (IllegalArgumentException exception) {
			output.println(exception.getMessage());
			return 2;
		} catch (RuntimeException exception) {
			output.println("X authentication state was not created. See application logs for browser/runtime details.");
			return 3;
		} finally {
			if (request != null) {
				request.clearPassword();
			}
		}
	}

	XAuthStateRequest buildRequest(String[] args) {
		boolean interactiveOnly = Arrays.asList(args == null ? new String[0] : args).contains("--interactive-only");
		String authStatePath = requiredEnv("X_AUTH_STATE_PATH");
		if (interactiveOnly) {
			return new XAuthStateRequest(null, null, null, Path.of(authStatePath), true);
		}
		String login = requiredEnv("X_LOGIN");
		String username = optionalEnv("X_USERNAME", login);
		char[] password = password();
		return new XAuthStateRequest(login, username, password, Path.of(authStatePath), false);
	}

	private void createStorageState(XAuthStateRequest request) {
		createParentDirectory(request.authStatePath());
		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
						.setHeadless(false));
				BrowserContext context = browser.newContext()) {
			Page page = context.newPage();
			page.navigate(LOGIN_URL);
			if (request.interactiveOnly()) {
				output.println("Complete the X verification step in the opened browser.");
			} else {
				performLogin(page, request);
			}
			if (!waitUntilAuthenticated(page, DEFAULT_TIMEOUT_MS)) {
				throw new IllegalStateException("X authentication did not complete before timeout");
			}
			context.storageState(new BrowserContext.StorageStateOptions().setPath(request.authStatePath()));
		}
	}

	private void performLogin(Page page, XAuthStateRequest request) {
		fillFirst(page, USERNAME_INPUT, request.login());
		clickFirstOrPressEnter(page, NEXT_BUTTON, USERNAME_INPUT);
		page.waitForTimeout(1_500);
		if (locatorCount(page, PASSWORD_INPUT) == 0 && locatorCount(page, USERNAME_INPUT) > 0) {
			fillFirst(page, USERNAME_INPUT, request.username());
			clickFirstOrPressEnter(page, NEXT_BUTTON, USERNAME_INPUT);
			page.waitForTimeout(1_500);
		}
		if (locatorCount(page, PASSWORD_INPUT) == 0) {
			output.println("Complete the X verification step in the opened browser.");
			return;
		}
		fillFirst(page, PASSWORD_INPUT, new String(request.password()));
		clickFirstOrPressEnter(page, LOGIN_BUTTON, PASSWORD_INPUT);
	}

	private boolean waitUntilAuthenticated(Page page, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		boolean promptedForChallenge = false;
		while (System.currentTimeMillis() < deadline) {
			XAuthenticationStatus status = XAuthenticationPageInspector.inspect(page.url(), page.content());
			if (status == XAuthenticationStatus.AUTHENTICATED) {
				return true;
			}
			if (status == XAuthenticationStatus.CHALLENGE_REQUIRED && !promptedForChallenge) {
				output.println("Complete the X verification step in the opened browser.");
				promptedForChallenge = true;
			}
			page.waitForTimeout(1_000);
		}
		return false;
	}

	private void fillFirst(Page page, String selector, String value) {
		Locator locator = page.locator(selector).first();
		locator.waitFor(new Locator.WaitForOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		locator.fill(value);
	}

	private void clickFirstOrPressEnter(Page page, String buttonSelector, String fallbackSelector) {
		Locator button = page.locator(buttonSelector).first();
		if (button.count() > 0) {
			button.click();
			return;
		}
		page.locator(fallbackSelector).first().press("Enter");
	}

	private int locatorCount(Page page, String selector) {
		return page.locator(selector).count();
	}

	private char[] password() {
		String passwordFromEnvironment = environment.get("X_PASSWORD");
		if (passwordFromEnvironment != null && !passwordFromEnvironment.isBlank()) {
			return passwordFromEnvironment.toCharArray();
		}
		if (console == null) {
			throw new IllegalArgumentException("X_PASSWORD environment variable is required when no interactive console is available.");
		}
		char[] password = console.readPassword("X password: ");
		if (password == null || password.length == 0) {
			throw new IllegalArgumentException("X password must not be blank.");
		}
		return password;
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

	private void createParentDirectory(Path authStatePath) {
		Path parent = authStatePath.toAbsolutePath().normalize().getParent();
		if (parent == null) {
			return;
		}
		try {
			Files.createDirectories(parent);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Failed to create parent directory for X authentication state file.");
		}
	}

	record XAuthStateRequest(
			String login,
			String username,
			char[] password,
			Path authStatePath,
			boolean interactiveOnly
	) {

		void clearPassword() {
			if (password != null) {
				Arrays.fill(password, '\0');
			}
		}
	}
}
