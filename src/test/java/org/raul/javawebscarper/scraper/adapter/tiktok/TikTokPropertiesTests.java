package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class TikTokPropertiesTests {

	@Test
	void bindsConfiguredProperties() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("scraper.tiktok.enabled", "false")
				.withProperty("scraper.tiktok.base-url", "https://www.tiktok.com")
				.withProperty("scraper.tiktok.auth-state-path", "playwright/.auth/tiktok-storage-state.json")
				.withProperty("scraper.tiktok.authentication-mode", "AUTHENTICATED")
				.withProperty("scraper.tiktok.authentication-required", "true")
				.withProperty("scraper.tiktok.login-url", "https://www.tiktok.com/login")
				.withProperty("scraper.tiktok.auth-verification-url", "https://www.tiktok.com/")
				.withProperty("scraper.tiktok.login-timeout-ms", "61000")
				.withProperty("scraper.tiktok.manual-verification-timeout-ms", "310000")
				.withProperty("scraper.tiktok.locale", "az-AZ")
				.withProperty("scraper.tiktok.timezone-id", "Asia/Baku")
				.withProperty("scraper.tiktok.allow-anonymous-fallback", "false")
				.withProperty("scraper.tiktok.search-mode", "HASHTAG")
				.withProperty("scraper.tiktok.navigation-timeout-ms", "31000")
				.withProperty("scraper.tiktok.readiness-timeout-ms", "21000")
				.withProperty("scraper.tiktok.search-timeout-ms", "22000")
				.withProperty("scraper.tiktok.post-open-timeout-ms", "23000")
				.withProperty("scraper.tiktok.max-scroll-attempts", "12")
				.withProperty("scraper.tiktok.no-new-post-limit", "2")
				.withProperty("scraper.tiktok.max-candidates", "60")
				.withProperty("scraper.tiktok.action-delay-ms", "300")
				.withProperty("scraper.tiktok.scroll-delay-ms", "900")
				.withProperty("scraper.tiktok.open-post-for-details", "false")
				.withProperty("scraper.tiktok.include-sponsored", "true")
				.withProperty("scraper.tiktok.include-photo-posts", "false");

		TikTokProperties properties = Binder.get(environment)
				.bind("scraper.tiktok", Bindable.of(TikTokProperties.class))
				.orElseThrow(() -> new IllegalStateException("Failed to bind TikTok properties"));

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.getBaseUrl()).isEqualTo("https://www.tiktok.com");
		assertThat(properties.getAuthStatePath()).isEqualTo("playwright/.auth/tiktok-storage-state.json");
		assertThat(properties.getAuthenticationMode()).isEqualTo(TikTokAuthenticationMode.AUTHENTICATED);
		assertThat(properties.isAuthenticationRequired()).isTrue();
		assertThat(properties.getLoginUrl()).isEqualTo("https://www.tiktok.com/login");
		assertThat(properties.getAuthVerificationUrl()).isEqualTo("https://www.tiktok.com/");
		assertThat(properties.getLoginTimeoutMs()).isEqualTo(61_000);
		assertThat(properties.getManualVerificationTimeoutMs()).isEqualTo(310_000);
		assertThat(properties.getLocale()).isEqualTo("az-AZ");
		assertThat(properties.getTimezoneId()).isEqualTo("Asia/Baku");
		assertThat(properties.isAllowAnonymousFallback()).isFalse();
		assertThat(properties.getSearchMode()).isEqualTo(TikTokSearchMode.HASHTAG);
		assertThat(properties.getNavigationTimeoutMs()).isEqualTo(31_000);
		assertThat(properties.getReadinessTimeoutMs()).isEqualTo(21_000);
		assertThat(properties.getSearchTimeoutMs()).isEqualTo(22_000);
		assertThat(properties.getPostOpenTimeoutMs()).isEqualTo(23_000);
		assertThat(properties.getMaxScrollAttempts()).isEqualTo(12);
		assertThat(properties.getNoNewPostLimit()).isEqualTo(2);
		assertThat(properties.getMaxCandidates()).isEqualTo(60);
		assertThat(properties.getActionDelayMs()).isEqualTo(300);
		assertThat(properties.getScrollDelayMs()).isEqualTo(900);
		assertThat(properties.isOpenPostForDetails()).isFalse();
		assertThat(properties.isIncludeSponsored()).isTrue();
		assertThat(properties.isIncludePhotoPosts()).isFalse();
	}

	@Test
	void defaultsRequireAuthenticatedState() {
		TikTokProperties properties = new TikTokProperties();

		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getBaseUrl()).isEqualTo("https://www.tiktok.com");
		assertThat(properties.getAuthStatePath()).isEmpty();
		assertThat(properties.getAuthenticationMode()).isEqualTo(TikTokAuthenticationMode.AUTHENTICATED);
		assertThat(properties.isAuthenticationRequired()).isTrue();
		assertThat(properties.getLoginUrl()).isEqualTo("https://www.tiktok.com/login");
		assertThat(properties.getAuthVerificationUrl()).isEqualTo("https://www.tiktok.com/");
		assertThat(properties.getLoginTimeoutMs()).isEqualTo(60_000);
		assertThat(properties.getManualVerificationTimeoutMs()).isEqualTo(300_000);
		assertThat(properties.getLocale()).isEqualTo("az-AZ");
		assertThat(properties.isAllowAnonymousFallback()).isFalse();
		assertThat(properties.getSearchMode()).isEqualTo(TikTokSearchMode.AUTO);
		assertThat(properties.isIncludeSponsored()).isFalse();
		assertThat(properties.isIncludePhotoPosts()).isTrue();
	}
}
