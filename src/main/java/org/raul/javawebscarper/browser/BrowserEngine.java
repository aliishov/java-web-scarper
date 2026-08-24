package org.raul.javawebscarper.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserEngine {

	private final BrowserEngineProperties properties;
	private final Object lifecycleLock = new Object();

	private final ThreadLocal<BrowserRuntime> scraperRuntime = new ThreadLocal<>();
	private final ThreadLocal<BrowserRuntime> interactiveRuntime = new ThreadLocal<>();
	private final Set<BrowserRuntime> runtimes = ConcurrentHashMap.newKeySet();

	@PostConstruct
	void onStartup() {
		if (!properties.isEnabled()) {
			log.info("Playwright browser engine is disabled");
			return;
		}
		if (properties.isInstallBrowsersOnStartup()) {
			log.warn(
					"scraper.browser.install-browsers-on-startup is true, but browsers are not installed automatically. "
							+ "Run the documented Playwright install command explicitly."
			);
		}
		log.info(
				"Playwright browser engine is enabled: browserType={}, headless={}, lazyInitialization=true",
				properties.getBrowserType(),
				properties.isHeadless()
		);
	}

	public Browser getBrowser() {
		return getOrCreateRuntime(scraperRuntime, properties.isHeadless(), "scraper").browser();
	}

	/**
	 * Starts a visible browser only for an admin-initiated social account connection.
	 * Scraper jobs continue using the configured background browser.
	 */
	public Browser getInteractiveBrowser() {
		return getOrCreateRuntime(interactiveRuntime, false, "interactive social login").browser();
	}

	/**
	 * Creates the context used for a visible account-connection flow. It must use
	 * the same browser identity as scraper contexts, otherwise platforms can bind
	 * the newly-issued cookies to one user agent and reject later scraper requests
	 * made with another.
	 */
	public BrowserContext newInteractiveContext() {
		Browser.NewContextOptions options = new Browser.NewContextOptions()
				.setViewportSize(properties.getViewportWidth(), properties.getViewportHeight())
				.setUserAgent(properties.getUserAgent());
		BrowserContext context = getInteractiveBrowser().newContext(options);
		context.setDefaultTimeout(properties.getActionTimeoutMs());
		context.setDefaultNavigationTimeout(properties.getNavigationTimeoutMs());
		return context;
	}

	public boolean isEnabled() {
		return properties.isEnabled();
	}

	public boolean isInitialized() {
		return !runtimes.isEmpty();
	}

	public BrowserType getBrowserType() {
		return properties.getBrowserType();
	}

	@PreDestroy
	public void shutdown() {
		synchronized (lifecycleLock) {
			for (BrowserRuntime runtime : runtimes) {
				closeRuntime(runtime);
			}
			runtimes.clear();
			scraperRuntime.remove();
			interactiveRuntime.remove();
		}
	}

	private BrowserRuntime getOrCreateRuntime(ThreadLocal<BrowserRuntime> runtimeHolder, boolean headless, String purpose) {
		BrowserRuntime runtime = runtimeHolder.get();
		if (runtime != null && runtime.browser().isConnected()) {
			return runtime;
		}

		synchronized (lifecycleLock) {
			runtime = runtimeHolder.get();
			if (runtime != null && runtime.browser().isConnected()) {
				return runtime;
			}
			if (runtime != null) {
				closeRuntime(runtime);
			}
			BrowserRuntime createdRuntime = initializeRuntime(headless, purpose);
			runtimeHolder.set(createdRuntime);
			runtimes.add(createdRuntime);
			return createdRuntime;
		}
	}

	private BrowserRuntime initializeRuntime(boolean headless, String purpose) {
		if (!properties.isEnabled()) {
			throw new BrowserEngineException("Playwright browser engine is disabled");
		}
		Playwright createdPlaywright = null;
		Browser launchedBrowser = null;
		try {
			log.info(
					"Starting Playwright browser: purpose={}, type={}, headless={}, thread={}",
					purpose,
					properties.getBrowserType(),
					headless,
					Thread.currentThread().getName()
			);
			createdPlaywright = Playwright.create();
			launchedBrowser = launchBrowser(createdPlaywright, headless);
			log.info(
					"Playwright browser started: purpose={}, type={}, thread={}",
					purpose,
					properties.getBrowserType(),
					Thread.currentThread().getName()
			);
			return new BrowserRuntime(createdPlaywright, launchedBrowser);
		} catch (PlaywrightException exception) {
			log.error("Playwright browser could not start: type={}, headless={}, executablePath={}",
					properties.getBrowserType(), headless, properties.getExecutablePath(), exception);
			closeRuntime(new BrowserRuntime(createdPlaywright, launchedBrowser));
			throw new BrowserEngineException("Failed to start Playwright browser", exception);
		}
	}

	private Browser launchBrowser(Playwright playwrightInstance, boolean headless) {
		com.microsoft.playwright.BrowserType.LaunchOptions options =
				new com.microsoft.playwright.BrowserType.LaunchOptions()
						.setHeadless(headless)
						.setTimeout(properties.getLaunchTimeoutMs())
						.setSlowMo(properties.getSlowMoMs());
		if (!headless) {
			// Playwright can inherit an invalid Wayland selection on Linux desktops.
			// Chromium's X11 backend works with the local IntelliJ development session.
			options.setArgs(List.of("--ozone-platform=x11"));
		}
		if (properties.getExecutablePath() != null && !properties.getExecutablePath().isBlank()) {
			options.setExecutablePath(Path.of(properties.getExecutablePath().trim()).toAbsolutePath().normalize());
			log.info("Using configured system browser executable for Playwright");
		}

		return switch (properties.getBrowserType()) {
			case CHROMIUM -> playwrightInstance.chromium().launch(options);
			case FIREFOX -> playwrightInstance.firefox().launch(options);
			case WEBKIT -> playwrightInstance.webkit().launch(options);
		};
	}

	private void closeRuntime(BrowserRuntime runtime) {
		if (runtime == null) {
			return;
		}
		runtimes.remove(runtime);
		if (runtime.browser() != null) {
			try {
				log.info("Stopping Playwright browser: type={}", properties.getBrowserType());
				runtime.browser().close();
			} catch (PlaywrightException exception) {
				log.warn("Failed to close Playwright browser cleanly", exception);
			}
		}
		if (runtime.playwright() != null) {
			try {
				runtime.playwright().close();
			} catch (PlaywrightException exception) {
				log.warn("Failed to close Playwright cleanly", exception);
			}
		}
	}

	private record BrowserRuntime(Playwright playwright, Browser browser) {
	}
}
