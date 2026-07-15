package org.raul.javawebscarper.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrowserSessionFactory {

	private final BrowserEngine browserEngine;
	private final BrowserEngineProperties properties;

	public BrowserSession createSession() {
		return createSession(BrowserSessionOptions.defaults());
	}

	public BrowserSession createSession(BrowserSessionOptions options) {
		BrowserContext context = null;
		try {
			Browser browser = browserEngine.getBrowser();
			BrowserSessionOptions safeOptions = options == null ? BrowserSessionOptions.defaults() : options;
			Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
					.setViewportSize(properties.getViewportWidth(), properties.getViewportHeight())
					.setUserAgent(firstNonBlank(safeOptions.userAgent(), properties.getUserAgent()));
			if (safeOptions.storageStatePath() != null) {
				Path storageStatePath = validateStorageStatePath(safeOptions.storageStatePath());
				log.info("Using configured storage state for authenticated browser session");
				contextOptions.setStorageStatePath(storageStatePath);
			}
			if (safeOptions.locale() != null && !safeOptions.locale().isBlank()) {
				contextOptions.setLocale(safeOptions.locale());
			}
			if (safeOptions.timezoneId() != null && !safeOptions.timezoneId().isBlank()) {
				contextOptions.setTimezoneId(safeOptions.timezoneId());
			}
			Map<String, String> extraHeaders = safeOptions.extraHttpHeaders();
			if (extraHeaders != null && !extraHeaders.isEmpty()) {
				contextOptions.setExtraHTTPHeaders(extraHeaders);
			}
			context = browser.newContext(contextOptions);
			context.setDefaultTimeout(properties.getActionTimeoutMs());
			context.setDefaultNavigationTimeout(properties.getNavigationTimeoutMs());

			Page page = context.newPage();
			page.setDefaultTimeout(properties.getActionTimeoutMs());
			page.setDefaultNavigationTimeout(properties.getNavigationTimeoutMs());

			log.debug(
					"Created browser session: viewport={}x{}",
					properties.getViewportWidth(),
					properties.getViewportHeight()
			);
			return new BrowserSession(context, page, properties);
		} catch (PlaywrightException exception) {
			closeContextQuietly(context);
			throw new BrowserEngineException("Failed to create browser session", exception);
		}
	}

	private String firstNonBlank(String first, String second) {
		return first == null || first.isBlank() ? second : first;
	}

	private Path validateStorageStatePath(Path storageStatePath) {
		if (!Files.isRegularFile(storageStatePath)) {
			throw new BrowserEngineException("Configured browser storage state file does not exist");
		}
		return storageStatePath;
	}

	private void closeContextQuietly(BrowserContext context) {
		if (context == null) {
			return;
		}
		try {
			context.close();
		} catch (PlaywrightException exception) {
			log.warn("Failed to close browser context after session creation failure", exception);
		}
	}
}
