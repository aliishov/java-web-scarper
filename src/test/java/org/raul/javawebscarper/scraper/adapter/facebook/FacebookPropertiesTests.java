package org.raul.javawebscarper.scraper.adapter.facebook;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class FacebookPropertiesTests {

	@Test
	void bindsConfiguredProperties() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("scraper.facebook.enabled", "false")
				.withProperty("scraper.facebook.base-url", "https://www.facebook.com/")
				.withProperty("scraper.facebook.auth-state-path", "playwright/.auth/facebook-storage-state.json")
				.withProperty("scraper.facebook.authentication-required", "false")
				.withProperty("scraper.facebook.search-mode", "TOP")
				.withProperty("scraper.facebook.max-scroll-attempts", "12")
				.withProperty("scraper.facebook.no-new-post-limit", "2")
				.withProperty("scraper.facebook.scroll-delay-ms", "900")
				.withProperty("scraper.facebook.include-sponsored", "true")
				.withProperty("scraper.facebook.include-reels", "true")
				.withProperty("scraper.facebook.open-post-for-details", "false");

		FacebookProperties properties = Binder.get(environment)
				.bind("scraper.facebook", Bindable.of(FacebookProperties.class))
				.orElseThrow(() -> new IllegalStateException("Failed to bind Facebook properties"));

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.getBaseUrl()).isEqualTo("https://www.facebook.com/");
		assertThat(properties.getAuthStatePath()).isEqualTo("playwright/.auth/facebook-storage-state.json");
		assertThat(properties.isAuthenticationRequired()).isFalse();
		assertThat(properties.getSearchMode()).isEqualTo(FacebookSearchMode.TOP);
		assertThat(properties.getMaxScrollAttempts()).isEqualTo(12);
		assertThat(properties.getNoNewPostLimit()).isEqualTo(2);
		assertThat(properties.getScrollDelayMs()).isEqualTo(900);
		assertThat(properties.isIncludeSponsored()).isTrue();
		assertThat(properties.isIncludeReels()).isTrue();
		assertThat(properties.isOpenPostForDetails()).isFalse();
	}

	@Test
	void defaultsRequireExternalAuthenticationState() {
		FacebookProperties properties = new FacebookProperties();

		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getBaseUrl()).isEqualTo("https://www.facebook.com/");
		assertThat(properties.getAuthStatePath()).isEmpty();
		assertThat(properties.isAuthenticationRequired()).isTrue();
		assertThat(properties.getSearchMode()).isEqualTo(FacebookSearchMode.RECENT);
		assertThat(properties.isIncludeSponsored()).isFalse();
		assertThat(properties.isIncludeReels()).isFalse();
	}
}
