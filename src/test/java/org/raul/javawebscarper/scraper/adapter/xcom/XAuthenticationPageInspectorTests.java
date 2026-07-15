package org.raul.javawebscarper.scraper.adapter.xcom;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XAuthenticationPageInspectorTests {

	@Test
	void detectsAuthenticatedHomePage() {
		String html = """
				<html>
				  <body>
				    <a data-testid="AppTabBar_Home_Link" href="/home">Home</a>
				    <div data-testid="SideNav_AccountSwitcher_Button">Account</div>
				  </body>
				</html>
				""";

		assertThat(XAuthenticationPageInspector.inspect("https://x.com/home", html))
				.isEqualTo(XAuthenticationStatus.AUTHENTICATED);
	}

	@Test
	void detectsLoginRedirectAsAuthRequired() {
		assertThat(XAuthenticationPageInspector.inspect("https://x.com/i/flow/login", "<html><body></body></html>"))
				.isEqualTo(XAuthenticationStatus.AUTH_REQUIRED);
	}

	@Test
	void detectsLoginFormAsExpiredState() {
		String html = """
				<html>
				  <body>
				    <input autocomplete="username" name="text">
				    <input type="password">
				  </body>
				</html>
				""";

		assertThat(XAuthenticationPageInspector.inspect("https://x.com/home", html))
				.isEqualTo(XAuthenticationStatus.AUTH_STATE_EXPIRED);
	}

	@Test
	void detectsChallengeRequired() {
		String html = """
				<html>
				  <body>Verify your identity by entering the confirmation code.</body>
				</html>
				""";

		assertThat(XAuthenticationPageInspector.inspect("https://x.com/account/access", html))
				.isEqualTo(XAuthenticationStatus.CHALLENGE_REQUIRED);
	}

	@Test
	void detectsRateLimitedPage() {
		assertThat(XAuthenticationPageInspector.inspect("https://x.com/home", "<html><body>Rate limit exceeded</body></html>"))
				.isEqualTo(XAuthenticationStatus.RATE_LIMITED);
	}
}
