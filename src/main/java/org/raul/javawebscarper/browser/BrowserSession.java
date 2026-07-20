package org.raul.javawebscarper.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class BrowserSession implements AutoCloseable {

	private final BrowserContext context;
	private final Page page;
	private final BrowserEngineProperties properties;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	public BrowserSession(BrowserContext context, Page page, BrowserEngineProperties properties) {
		this.context = context;
		this.page = page;
		this.properties = properties;
	}

	public BrowserPage newPage() {
		ensureOpen();
		return new BrowserPage(page, properties);
	}

	public BrowserPage openNewPage() {
		ensureOpen();
		Page newPage = context.newPage();
		newPage.setDefaultTimeout(properties.getActionTimeoutMs());
		newPage.setDefaultNavigationTimeout(properties.getNavigationTimeoutMs());
		return new BrowserPage(newPage, properties);
	}

	public BrowserPage getPage() {
		return newPage();
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		closePage();
		closeContext();
	}

	private void ensureOpen() {
		if (closed.get()) {
			throw new BrowserEngineException("Browser session is already closed");
		}
	}

	private void closePage() {
		try {
			page.close();
		} catch (PlaywrightException exception) {
			log.warn("Failed to close browser page cleanly", exception);
		}
	}

	private void closeContext() {
		try {
			context.close();
		} catch (PlaywrightException exception) {
			log.warn("Failed to close browser context cleanly", exception);
		}
	}
}
