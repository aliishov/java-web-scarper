package org.raul.javawebscarper.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrowserSessionTests {

	@Test
	void closesPageAndContext() {
		BrowserContext context = mock(BrowserContext.class);
		Page page = mock(Page.class);
		BrowserSession session = new BrowserSession(context, page, new BrowserEngineProperties());

		session.close();

		verify(page).close();
		verify(context).close();
	}

	@Test
	void rejectsNewPageAfterClose() {
		BrowserContext context = mock(BrowserContext.class);
		Page page = mock(Page.class);
		BrowserSession session = new BrowserSession(context, page, new BrowserEngineProperties());

		session.close();

		assertThatThrownBy(session::newPage)
				.isInstanceOf(BrowserEngineException.class)
				.hasMessage("Browser session is already closed");
	}

	@Test
	void opensAdditionalPageInSameContext() {
		BrowserContext context = mock(BrowserContext.class);
		Page primaryPage = mock(Page.class);
		Page additionalPage = mock(Page.class);
		BrowserEngineProperties properties = new BrowserEngineProperties();
		when(context.newPage()).thenReturn(additionalPage);
		BrowserSession session = new BrowserSession(context, primaryPage, properties);

		session.openNewPage();

		verify(context).newPage();
		verify(additionalPage).setDefaultTimeout(properties.getActionTimeoutMs());
		verify(additionalPage).setDefaultNavigationTimeout(properties.getNavigationTimeoutMs());
	}
}
