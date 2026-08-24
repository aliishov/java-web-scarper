package org.raul.javawebscarper.browser;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "scraper.browser")
public class BrowserEngineProperties {

	private boolean enabled = true;
	private boolean headless = true;
	private BrowserType browserType = BrowserType.CHROMIUM;
	/** Optional path to an OS-managed browser binary (recommended on rolling Linux distributions). */
	@Size(max = 1024)
	private String executablePath = "";
	@Positive
	private long launchTimeoutMs = 30_000;
	@Positive
	private long navigationTimeoutMs = 30_000;
	@Positive
	private long actionTimeoutMs = 10_000;
	@PositiveOrZero
	private long defaultWaitMs = 1_000;
	@Positive
	private int viewportWidth = 1_366;
	@Positive
	private int viewportHeight = 768;
	@NotBlank
	@Size(max = 512)
	private String userAgent = "Mozilla/5.0 (X11; Linux x86_64) "
			+ "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36";
	@PositiveOrZero
	private long slowMoMs = 0;
	@Positive
	private int maxScrollAttempts = 10;
	@PositiveOrZero
	private long scrollDelayMs = 1_000;
	private boolean installBrowsersOnStartup = false;
}
