package org.raul.javawebscarper.scraper.adapter.instagram;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class InstagramPropertiesTests {

	@Test
	void bindsConfiguredProperties() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("scraper.instagram.enabled", "false")
				.withProperty("scraper.instagram.base-url", "https://www.instagram.com")
				.withProperty("scraper.instagram.auth-state-path", "playwright/.auth/instagram-storage-state.json")
				.withProperty("scraper.instagram.authentication-required", "false")
				.withProperty("scraper.instagram.login-url", "https://www.instagram.com/accounts/login/")
				.withProperty("scraper.instagram.auth-verification-url", "https://www.instagram.com/")
				.withProperty("scraper.instagram.login-timeout-ms", "70000")
				.withProperty("scraper.instagram.manual-verification-timeout-ms", "310000")
				.withProperty("scraper.instagram.locale", "az-AZ")
				.withProperty("scraper.instagram.timezone-id", "Asia/Baku")
				.withProperty("scraper.instagram.search-mode", "HASHTAG")
				.withProperty("scraper.instagram.max-scroll-attempts", "12")
				.withProperty("scraper.instagram.no-new-post-limit", "2")
				.withProperty("scraper.instagram.max-candidates", "60")
				.withProperty("scraper.instagram.max-carousel-items", "10")
				.withProperty("scraper.instagram.scroll-delay-ms", "900")
				.withProperty("scraper.instagram.action-delay-ms", "300")
				.withProperty("scraper.instagram.post-open-timeout-ms", "10000")
				.withProperty("scraper.instagram.timeline-load-timeout-ms", "18000")
				.withProperty("scraper.instagram.include-reels", "true")
				.withProperty("scraper.instagram.include-sponsored", "true")
				.withProperty("scraper.instagram.open-post-for-details", "false");

		InstagramProperties properties = Binder.get(environment)
				.bind("scraper.instagram", Bindable.of(InstagramProperties.class))
				.orElseThrow(() -> new IllegalStateException("Failed to bind Instagram properties"));

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.getBaseUrl()).isEqualTo("https://www.instagram.com");
		assertThat(properties.getAuthStatePath()).isEqualTo("playwright/.auth/instagram-storage-state.json");
		assertThat(properties.isAuthenticationRequired()).isFalse();
		assertThat(properties.getLoginUrl()).isEqualTo("https://www.instagram.com/accounts/login/");
		assertThat(properties.getAuthVerificationUrl()).isEqualTo("https://www.instagram.com/");
		assertThat(properties.getLoginTimeoutMs()).isEqualTo(70_000);
		assertThat(properties.getManualVerificationTimeoutMs()).isEqualTo(310_000);
		assertThat(properties.getLocale()).isEqualTo("az-AZ");
		assertThat(properties.getTimezoneId()).isEqualTo("Asia/Baku");
		assertThat(properties.getSearchMode()).isEqualTo(InstagramSearchMode.HASHTAG);
		assertThat(properties.getMaxScrollAttempts()).isEqualTo(12);
		assertThat(properties.getNoNewPostLimit()).isEqualTo(2);
		assertThat(properties.getMaxCandidates()).isEqualTo(60);
		assertThat(properties.getMaxCarouselItems()).isEqualTo(10);
		assertThat(properties.getScrollDelayMs()).isEqualTo(900);
		assertThat(properties.getActionDelayMs()).isEqualTo(300);
		assertThat(properties.getPostOpenTimeoutMs()).isEqualTo(10_000);
		assertThat(properties.getTimelineLoadTimeoutMs()).isEqualTo(18_000);
		assertThat(properties.isIncludeReels()).isTrue();
		assertThat(properties.isIncludeSponsored()).isTrue();
		assertThat(properties.isOpenPostForDetails()).isFalse();
	}

	@Test
	void defaultsRequireExternalAuthenticationState() {
		InstagramProperties properties = new InstagramProperties();

		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getBaseUrl()).isEqualTo("https://www.instagram.com");
		assertThat(properties.getAuthStatePath()).isEmpty();
		assertThat(properties.isAuthenticationRequired()).isTrue();
		assertThat(properties.getLoginUrl()).isEqualTo("https://www.instagram.com/accounts/login/");
		assertThat(properties.getAuthVerificationUrl()).isEqualTo("https://www.instagram.com/");
		assertThat(properties.getLocale()).isEqualTo("az-AZ");
		assertThat(properties.getSearchMode()).isEqualTo(InstagramSearchMode.AUTO);
		assertThat(properties.isIncludeReels()).isFalse();
		assertThat(properties.isIncludeSponsored()).isFalse();
	}
}
