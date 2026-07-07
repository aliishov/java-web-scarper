package org.raul.javawebscarper.browser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserEngineTests {

	@Test
	void disabledEngineDoesNotInitializeBrowser() {
		BrowserEngineProperties properties = new BrowserEngineProperties();
		properties.setEnabled(false);
		BrowserEngine browserEngine = new BrowserEngine(properties);

		assertThat(browserEngine.isEnabled()).isFalse();
		assertThat(browserEngine.isInitialized()).isFalse();
		assertThatThrownBy(browserEngine::getBrowser)
				.isInstanceOf(BrowserEngineException.class)
				.hasMessage("Playwright browser engine is disabled");
	}

	@Test
	void reportsConfiguredBrowserTypeWithoutStartingBrowser() {
		BrowserEngineProperties properties = new BrowserEngineProperties();
		properties.setBrowserType(BrowserType.WEBKIT);
		BrowserEngine browserEngine = new BrowserEngine(properties);

		assertThat(browserEngine.getBrowserType()).isEqualTo(BrowserType.WEBKIT);
		assertThat(browserEngine.isInitialized()).isFalse();
	}
}
