package org.raul.javawebscarper.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrowserPageTests {

	@Test
	void wrapsTimeoutPlaywrightException() {
		Page page = mock(Page.class);
		when(page.textContent(eq(".headline"), any(Page.TextContentOptions.class)))
				.thenThrow(new PlaywrightException("Timeout 10000ms exceeded"));
		BrowserPage browserPage = new BrowserPage(page, new BrowserEngineProperties());

		assertThatThrownBy(() -> browserPage.textContent(".headline"))
				.isInstanceOf(BrowserTimeoutException.class)
				.hasMessage("Browser timeout during textContent");
	}

	@Test
	void wrapsNonTimeoutPlaywrightException() {
		Page page = mock(Page.class);
		when(page.content()).thenThrow(new PlaywrightException("Page crashed"));
		BrowserPage browserPage = new BrowserPage(page, new BrowserEngineProperties());

		assertThatThrownBy(browserPage::content)
				.isInstanceOf(BrowserEngineException.class)
				.isNotInstanceOf(BrowserTimeoutException.class)
				.hasMessage("Browser action failed during content");
	}
}
