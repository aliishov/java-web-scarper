package org.raul.javawebscarper.tools.threadsauth;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ThreadsAuthStateGenerator {
	private final Map<String, String> environment;

	ThreadsAuthStateGenerator(Map<String, String> environment) {
		this.environment = environment;
	}

	public static void main(String[] args) {
		new ThreadsAuthStateGenerator(System.getenv()).run();
	}

	void run() {
		String configuredPath = required("THREADS_AUTH_STATE_PATH");
		Path path = Path.of(configuredPath).toAbsolutePath().normalize();
		try {
			Files.createDirectories(path.getParent());
			try (Playwright playwright = Playwright.create();
				 Browser browser = playwright.chromium().launch(
						 new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(false));
				 BrowserContext context = browser.newContext()) {
				Page page = context.newPage();
				page.navigate(environment.getOrDefault("THREADS_LOGIN_URL", "https://www.threads.net/login"));
				System.out.println("Complete Threads/Instagram login, 2FA, CAPTCHA, or checkpoint manually.");
				long deadline = System.currentTimeMillis() + 300_000;
				while (System.currentTimeMillis() < deadline && isLoginPage(page)) {
					page.waitForTimeout(1_500);
				}
				if (isLoginPage(page)) {
					throw new IllegalStateException("Threads authentication did not complete before timeout.");
				}
				context.storageState(new BrowserContext.StorageStateOptions().setPath(path));
				System.out.println("Threads authentication state was saved successfully.");
			}
		} catch (Exception exception) {
			throw new IllegalStateException("Threads authentication state was not created.", exception);
		}
	}

	private boolean isLoginPage(Page page) {
		String value = (page.url() + " " + page.content()).toLowerCase();
		return value.contains("/login") || value.contains("type=\"password\"")
				|| value.contains("log in with instagram") || value.contains("checkpoint");
	}

	private String required(String name) {
		String value = environment.get(name);
		if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " environment variable is required.");
		return value.trim();
	}
}
