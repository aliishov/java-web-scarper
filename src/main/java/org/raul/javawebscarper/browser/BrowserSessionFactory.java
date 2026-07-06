package org.raul.javawebscarper.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrowserSessionFactory {

	private final BrowserEngine browserEngine;
	private final BrowserEngineProperties properties;

	public BrowserSession createSession() {
		BrowserContext context = null;
		try {
			Browser browser = browserEngine.getBrowser();
			context = browser.newContext(new Browser.NewContextOptions()
					.setViewportSize(properties.getViewportWidth(), properties.getViewportHeight())
					.setUserAgent(properties.getUserAgent()));
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
