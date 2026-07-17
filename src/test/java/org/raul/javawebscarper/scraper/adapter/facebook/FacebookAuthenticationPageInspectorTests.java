package org.raul.javawebscarper.scraper.adapter.facebook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FacebookAuthenticationPageInspectorTests {

	@Test
	void detectsAuthenticatedHomePage() {
		String html = """
				<html>
				  <body>
				    <div role="navigation">
				      <a aria-label="Home" href="/home.php">Home</a>
				    </div>
				    <div role="search"><input placeholder="Search Facebook"></div>
				  </body>
				</html>
				""";

		assertThat(FacebookAuthenticationPageInspector.inspect("https://www.facebook.com/", html))
				.isEqualTo(FacebookAuthenticationStatus.AUTHENTICATED);
	}

	@Test
	void detectsLoginRedirectAsAuthRequired() {
		assertThat(FacebookAuthenticationPageInspector.inspect("https://www.facebook.com/login", "<html><body></body></html>"))
				.isEqualTo(FacebookAuthenticationStatus.AUTH_REQUIRED);
	}

	@Test
	void detectsLoginFormAsExpiredState() {
		String html = """
				<html>
				  <body>
				    <input id="email" name="email">
				    <input id="pass" name="pass" type="password">
				  </body>
				</html>
				""";

		assertThat(FacebookAuthenticationPageInspector.inspect("https://www.facebook.com/", html))
				.isEqualTo(FacebookAuthenticationStatus.AUTH_STATE_EXPIRED);
	}

	@Test
	void detectsCheckpointAndChallengeStates() {
		assertThat(FacebookAuthenticationPageInspector.inspect("https://www.facebook.com/checkpoint/123", "<html><body>Security check</body></html>"))
				.isEqualTo(FacebookAuthenticationStatus.CHECKPOINT_REQUIRED);
		assertThat(FacebookAuthenticationPageInspector.inspect("https://www.facebook.com/", "<html><body>Enter the code from your authenticator app.</body></html>"))
				.isEqualTo(FacebookAuthenticationStatus.CHALLENGE_REQUIRED);
	}

	@Test
	void detectsRateLimitAndAccountRestriction() {
		assertThat(FacebookAuthenticationPageInspector.inspect("https://www.facebook.com/", "<html><body>You're temporarily blocked. Try again later.</body></html>"))
				.isEqualTo(FacebookAuthenticationStatus.RATE_LIMITED);
		assertThat(FacebookAuthenticationPageInspector.inspect("https://www.facebook.com/", "<html><body>Your account has been restricted.</body></html>"))
				.isEqualTo(FacebookAuthenticationStatus.ACCOUNT_RESTRICTED);
	}
}
