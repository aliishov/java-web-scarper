package org.raul.javawebscarper.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class BrowserPage implements AutoCloseable {

	private final Page page;
	private final BrowserEngineProperties properties;

	public void navigate(String url) {
		String loggableUrl = loggableUrl(url);
		long startedAt = System.nanoTime();
		log.info("Browser navigation started: url={}", loggableUrl);
		try {
			execute("navigate", () -> {
				page.navigate(url, new Page.NavigateOptions().setTimeout(properties.getNavigationTimeoutMs()));
				return null;
			});
			log.info(
					"Browser navigation completed: url={}, finalUrl={}, durationMs={}",
					loggableUrl,
					loggableUrl(page.url()),
					elapsedMillis(startedAt)
			);
		} catch (RuntimeException exception) {
			log.warn(
					"Browser navigation failed: url={}, durationMs={}, error={}",
					loggableUrl,
					elapsedMillis(startedAt),
					exception.getMessage()
			);
			throw exception;
		}
	}

	public void waitForSelector(String selector) {
		waitForSelector(selector, properties.getActionTimeoutMs());
	}

	public void waitForSelector(String selector, long timeoutMs) {
		execute("waitForSelector", () -> {
			log.debug("Waiting for selector {}", selector);
			page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(timeoutMs));
			return null;
		});
	}

	public void click(String selector) {
		execute("click", () -> {
			log.debug("Clicking selector {}", selector);
			page.click(selector, new Page.ClickOptions().setTimeout(properties.getActionTimeoutMs()));
			return null;
		});
	}

	public void fill(String selector, String value) {
		execute("fill", () -> {
			log.debug("Filling selector {}", selector);
			page.fill(selector, value, new Page.FillOptions().setTimeout(properties.getActionTimeoutMs()));
			return null;
		});
	}

	public void press(String selector, String key) {
		execute("press", () -> {
			log.debug("Pressing key on selector {}", selector);
			page.press(selector, key, new Page.PressOptions().setTimeout(properties.getActionTimeoutMs()));
			return null;
		});
	}

	public String textContent(String selector) {
		return execute("textContent", () -> page.textContent(
				selector,
				new Page.TextContentOptions().setTimeout(properties.getActionTimeoutMs())
		));
	}

	public List<String> allTextContents(String selector) {
		return execute("allTextContents", () -> {
			waitForSelector(selector);
			Locator locator = page.locator(selector);
			return locator.allTextContents();
		});
	}

	public String getAttribute(String selector, String attribute) {
		return execute("getAttribute", () -> page.getAttribute(
				selector,
				attribute,
				new Page.GetAttributeOptions().setTimeout(properties.getActionTimeoutMs())
		));
	}

	public String content() {
		return execute("content", page::content);
	}

	public String url() {
		return execute("url", page::url);
	}

	public void waitForTimeout(long millis) {
		execute("waitForTimeout", () -> {
			page.waitForTimeout(millis);
			return null;
		});
	}

	public void scrollToBottom() {
		scrollToBottom(properties.getMaxScrollAttempts(), properties.getScrollDelayMs());
	}

	public void scrollToBottom(int maxAttempts, long delayMs) {
		execute("scrollToBottom", () -> {
			log.debug("Scrolling page to bottom: maxAttempts={}, delayMs={}", maxAttempts, delayMs);
			long previousHeight = -1;
			for (int attempt = 0; attempt < maxAttempts; attempt++) {
				long currentHeight = documentHeight();
				if (currentHeight == previousHeight) {
					break;
				}
				previousHeight = currentHeight;
				page.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
				page.waitForTimeout(delayMs);
			}
			return null;
		});
	}

	public void scrollBy(int pixels, long delayMs) {
		execute("scrollBy", () -> {
			log.debug("Scrolling page by {}px with delay {}ms", pixels, delayMs);
			page.evaluate("amount => window.scrollBy({ top: amount, behavior: 'smooth' })", pixels);
			page.waitForTimeout(delayMs);
			return null;
		});
	}

	public void screenshot(String path) {
		Objects.requireNonNull(path, "Screenshot path must not be null");
		execute("screenshot", () -> {
			page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(path)));
			return null;
		});
	}

	@Override
	public void close() {
		execute("close", () -> {
			page.close();
			return null;
		});
	}

	private long documentHeight() {
		Object height = page.evaluate("() => document.body.scrollHeight");
		if (height instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(String.valueOf(height));
	}

	private <T> T execute(String action, Supplier<T> supplier) {
		try {
			return supplier.get();
		} catch (TimeoutError exception) {
			throw new BrowserTimeoutException("Browser timeout during " + action, exception);
		} catch (PlaywrightException exception) {
			if (isTimeout(exception)) {
				throw new BrowserTimeoutException("Browser timeout during " + action, exception);
			}
			throw new BrowserEngineException("Browser action failed during " + action, exception);
		}
	}

	private boolean isTimeout(PlaywrightException exception) {
		String message = exception.getMessage();
		return message != null && message.toLowerCase(Locale.ROOT).contains("timeout");
	}

	private String loggableUrl(String url) {
		if (url == null) {
			return null;
		}
		try {
			URI uri = new URI(url);
			return new URI(
					uri.getScheme(),
					null,
					uri.getHost(),
					uri.getPort(),
					uri.getPath(),
					null,
					null
			).toString();
		} catch (URISyntaxException exception) {
			return "<invalid-url>";
		}
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
