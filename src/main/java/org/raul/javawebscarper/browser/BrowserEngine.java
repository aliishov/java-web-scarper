package org.raul.javawebscarper.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserEngine {

	private final BrowserEngineProperties properties;
	private final Object lifecycleLock = new Object();

	private Playwright playwright;
	private Browser browser;

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
		if (!properties.isEnabled()) {
			throw new BrowserEngineException("Playwright browser engine is disabled");
		}
		synchronized (lifecycleLock) {
			if (browser == null) {
				initializeBrowser();
			}
			return browser;
		}
	}

	public boolean isEnabled() {
		return properties.isEnabled();
	}

	public boolean isInitialized() {
		synchronized (lifecycleLock) {
			return browser != null;
		}
	}

	public BrowserEngineProperties.BrowserType getBrowserType() {
		return properties.getBrowserType();
	}

	@PreDestroy
	public void shutdown() {
		synchronized (lifecycleLock) {
			closeBrowser();
			closePlaywright();
		}
	}

	private void initializeBrowser() {
		try {
			log.info("Starting Playwright browser: type={}, headless={}", properties.getBrowserType(), properties.isHeadless());
			playwright = Playwright.create();
			browser = launchBrowser(playwright);
			log.info("Playwright browser started: type={}", properties.getBrowserType());
		} catch (PlaywrightException exception) {
			closeBrowser();
			closePlaywright();
			throw new BrowserEngineException("Failed to start Playwright browser", exception);
		}
	}

	private Browser launchBrowser(Playwright playwrightInstance) {
		com.microsoft.playwright.BrowserType.LaunchOptions options =
				new com.microsoft.playwright.BrowserType.LaunchOptions()
						.setHeadless(properties.isHeadless())
						.setTimeout(properties.getLaunchTimeoutMs())
						.setSlowMo(properties.getSlowMoMs());

		return switch (properties.getBrowserType()) {
			case CHROMIUM -> playwrightInstance.chromium().launch(options);
			case FIREFOX -> playwrightInstance.firefox().launch(options);
			case WEBKIT -> playwrightInstance.webkit().launch(options);
		};
	}

	private void closeBrowser() {
		if (browser == null) {
			return;
		}
		try {
			log.info("Stopping Playwright browser: type={}", properties.getBrowserType());
			browser.close();
		} catch (PlaywrightException exception) {
			log.warn("Failed to close Playwright browser cleanly", exception);
		} finally {
			browser = null;
		}
	}

	private void closePlaywright() {
		if (playwright == null) {
			return;
		}
		try {
			playwright.close();
		} catch (PlaywrightException exception) {
			log.warn("Failed to close Playwright cleanly", exception);
		} finally {
			playwright = null;
		}
	}
}
