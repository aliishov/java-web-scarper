package org.raul.javawebscarper.scraper.adapter.xcom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class XComPropertiesTests {

	@Test
	void bindsAuthenticationProperties() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("scraper.x.enabled", "false")
				.withProperty("scraper.x.base-url", "https://x.com")
				.withProperty("scraper.x.auth-state-path", "playwright/.auth/x-storage-state.json")
				.withProperty("scraper.x.login-url", "https://x.com/i/flow/login")
				.withProperty("scraper.x.login-timeout-ms", "90000")
				.withProperty("scraper.x.authentication-required", "false");

		XComProperties properties = Binder.get(environment)
				.bind("scraper.x", Bindable.of(XComProperties.class))
				.orElseThrow(() -> new IllegalStateException("Failed to bind X properties"));

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.getBaseUrl()).isEqualTo("https://x.com");
		assertThat(properties.getAuthStatePath()).isEqualTo("playwright/.auth/x-storage-state.json");
		assertThat(properties.getLoginUrl()).isEqualTo("https://x.com/i/flow/login");
		assertThat(properties.getLoginTimeoutMs()).isEqualTo(90_000);
		assertThat(properties.isAuthenticationRequired()).isFalse();
	}

	@Test
	void authenticationDefaultsAreSafe() {
		XComProperties properties = new XComProperties();

		assertThat(properties.getAuthStatePath()).isEmpty();
		assertThat(properties.getLoginUrl()).isEqualTo("https://x.com/i/flow/login");
		assertThat(properties.getLoginTimeoutMs()).isEqualTo(60_000);
		assertThat(properties.isAuthenticationRequired()).isTrue();
	}
}
