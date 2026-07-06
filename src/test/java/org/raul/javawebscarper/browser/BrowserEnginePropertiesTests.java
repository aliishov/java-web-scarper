package org.raul.javawebscarper.browser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserEnginePropertiesTests {

	@Test
	void bindsBrowserProperties() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("scraper.browser.enabled", "false")
				.withProperty("scraper.browser.headless", "false")
				.withProperty("scraper.browser.browser-type", "FIREFOX")
				.withProperty("scraper.browser.launch-timeout-ms", "45000")
				.withProperty("scraper.browser.navigation-timeout-ms", "35000")
				.withProperty("scraper.browser.action-timeout-ms", "12000")
				.withProperty("scraper.browser.default-wait-ms", "1500")
				.withProperty("scraper.browser.viewport-width", "1440")
				.withProperty("scraper.browser.viewport-height", "900")
				.withProperty("scraper.browser.user-agent", "test-agent")
				.withProperty("scraper.browser.slow-mo-ms", "50")
				.withProperty("scraper.browser.max-scroll-attempts", "8")
				.withProperty("scraper.browser.scroll-delay-ms", "750")
				.withProperty("scraper.browser.install-browsers-on-startup", "true");

		BrowserEngineProperties properties = Binder.get(environment)
				.bind("scraper.browser", Bindable.of(BrowserEngineProperties.class))
				.orElseThrow(() -> new IllegalStateException("Failed to bind browser properties"));

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.isHeadless()).isFalse();
		assertThat(properties.getBrowserType()).isEqualTo(BrowserEngineProperties.BrowserType.FIREFOX);
		assertThat(properties.getLaunchTimeoutMs()).isEqualTo(45_000);
		assertThat(properties.getNavigationTimeoutMs()).isEqualTo(35_000);
		assertThat(properties.getActionTimeoutMs()).isEqualTo(12_000);
		assertThat(properties.getDefaultWaitMs()).isEqualTo(1_500);
		assertThat(properties.getViewportWidth()).isEqualTo(1_440);
		assertThat(properties.getViewportHeight()).isEqualTo(900);
		assertThat(properties.getUserAgent()).isEqualTo("test-agent");
		assertThat(properties.getSlowMoMs()).isEqualTo(50);
		assertThat(properties.getMaxScrollAttempts()).isEqualTo(8);
		assertThat(properties.getScrollDelayMs()).isEqualTo(750);
		assertThat(properties.isInstallBrowsersOnStartup()).isTrue();
	}

	@Test
	void defaultsAreSafeForInfrastructureLayer() {
		BrowserEngineProperties properties = new BrowserEngineProperties();

		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.isHeadless()).isTrue();
		assertThat(properties.getBrowserType()).isEqualTo(BrowserEngineProperties.BrowserType.CHROMIUM);
		assertThat(properties.getLaunchTimeoutMs()).isEqualTo(30_000);
		assertThat(properties.getNavigationTimeoutMs()).isEqualTo(30_000);
		assertThat(properties.getActionTimeoutMs()).isEqualTo(10_000);
		assertThat(properties.getDefaultWaitMs()).isEqualTo(1_000);
		assertThat(properties.getViewportWidth()).isEqualTo(1_366);
		assertThat(properties.getViewportHeight()).isEqualTo(768);
		assertThat(properties.getUserAgent()).contains("Mozilla/5.0");
		assertThat(properties.getSlowMoMs()).isZero();
		assertThat(properties.getMaxScrollAttempts()).isEqualTo(10);
		assertThat(properties.getScrollDelayMs()).isEqualTo(1_000);
		assertThat(properties.isInstallBrowsersOnStartup()).isFalse();
	}
}
