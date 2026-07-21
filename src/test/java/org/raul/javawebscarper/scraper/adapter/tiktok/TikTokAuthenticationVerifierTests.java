package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TikTokAuthenticationVerifierTests {

	@Test
	void recognizesAnonymousAccessWithoutTreatingLoginButtonAsFailure() {
		String html = """
				<main>
				  <button>Log in</button>
				  <a href="/@aznews/video/7351234567890123456">video</a>
				</main>
				""";

		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/search/video?q=Baku", html))
				.isEqualTo(TikTokAuthenticationStatus.ANONYMOUS_ACCESS);
	}

	@Test
	void recognizesNonBlockingLoginModalAsAnonymousAccessWhenResultsAreVisible() {
		String html = """
				<main>
				  <div role="dialog"><button>Log in</button></div>
				  <a href="/@aznews/video/7351234567890123456">video</a>
				</main>
				""";

		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/search/video?q=Baku", html))
				.isEqualTo(TikTokAuthenticationStatus.ANONYMOUS_ACCESS);
	}

	@Test
	void recognizesAuthenticatedState() {
		String html = """
				<main data-e2e="app">
				  <a data-e2e="nav-profile" href="/@currentuser">Profile</a>
				  <input type="search">
				</main>
				""";

		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/", html))
				.isEqualTo(TikTokAuthenticationStatus.AUTHENTICATED);
	}

	@Test
	void recognizesAuthenticationFailuresAndBlockers() {
		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/login", "<form><input name='username'><input type='password'></form>"))
				.isEqualTo(TikTokAuthenticationStatus.AUTH_REQUIRED);
		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/", "<main><div role='dialog'>Log in to continue</div></main>"))
				.isEqualTo(TikTokAuthenticationStatus.LOGIN_MODAL_BLOCKING);
		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/", "<main>captcha verify you are human</main>"))
				.isEqualTo(TikTokAuthenticationStatus.CAPTCHA_REQUIRED);
		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/verify", "<main>Security verification</main>"))
				.isEqualTo(TikTokAuthenticationStatus.VERIFICATION_REQUIRED);
		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/", "<main>Enter the two-factor security code<input autocomplete='one-time-code'></main>"))
				.isEqualTo(TikTokAuthenticationStatus.TWO_FACTOR_REQUIRED);
		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/", "<main>Too many attempts. Try again later.</main>"))
				.isEqualTo(TikTokAuthenticationStatus.RATE_LIMITED);
		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/", "<main>Temporarily blocked. Try again later.</main>"))
				.isEqualTo(TikTokAuthenticationStatus.TEMPORARILY_BLOCKED);
		assertThat(TikTokAuthenticationVerifier.inspect("https://www.tiktok.com/", "<main>Account restricted</main>"))
				.isEqualTo(TikTokAuthenticationStatus.ACCOUNT_RESTRICTED);
	}
}
