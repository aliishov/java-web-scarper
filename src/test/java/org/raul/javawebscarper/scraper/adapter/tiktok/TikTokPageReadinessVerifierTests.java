package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TikTokPageReadinessVerifierTests {

	@Test
	void recognizesReadySearchPageWithVideoLinks() {
		String html = """
				<html><body><main><a href="/@aznews/video/7351234567890123456">video</a></main></body></html>
				""";

		assertThat(TikTokPageReadinessVerifier.inspect("https://www.tiktok.com/search/video?q=Baku", html))
				.isEqualTo(TikTokPageReadinessStatus.READY);
	}

	@Test
	void regressionOldEnsureReadyDoesNotRequireSingleSearchInputSelector() {
		String html = """
				<html><body><main data-e2e="search-result-container">
				  <article><a href="/@aznews/video/7351234567890123456">video</a></article>
				</main></body></html>
				""";

		assertThat(TikTokPageReadinessVerifier.inspect("https://www.tiktok.com/search/video?q=M%C9%99hk%C9%99m%C9%99", html))
				.isEqualTo(TikTokPageReadinessStatus.READY);
	}

	@Test
	void recognizesBlockerAndTransientStates() {
		assertThat(TikTokPageReadinessVerifier.inspect("https://www.tiktok.com/", "<body>Loading...</body>"))
				.isEqualTo(TikTokPageReadinessStatus.LOADING);
		assertThat(TikTokPageReadinessVerifier.inspect("https://www.tiktok.com/", "<main>captcha verify you are human</main>"))
				.isEqualTo(TikTokPageReadinessStatus.CAPTCHA);
		assertThat(TikTokPageReadinessVerifier.inspect("https://www.tiktok.com/verify", "<main>Security verification</main>"))
				.isEqualTo(TikTokPageReadinessStatus.VERIFICATION);
		assertThat(TikTokPageReadinessVerifier.inspect("https://www.tiktok.com/", "<main>Too many attempts. Try again later.</main>"))
				.isEqualTo(TikTokPageReadinessStatus.RATE_LIMITED);
		assertThat(TikTokPageReadinessVerifier.inspect("https://www.tiktok.com/login", "<form><input name='username'><input type='password'></form>"))
				.isEqualTo(TikTokPageReadinessStatus.LOGIN_MODAL);
		assertThat(TikTokPageReadinessVerifier.inspect("https://www.tiktok.com/", "<main><button>Accept all cookies</button></main>"))
				.isEqualTo(TikTokPageReadinessStatus.COOKIE_CONSENT);
	}
}
