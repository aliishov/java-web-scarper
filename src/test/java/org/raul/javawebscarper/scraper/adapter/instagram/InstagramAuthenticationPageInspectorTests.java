package org.raul.javawebscarper.scraper.adapter.instagram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstagramAuthenticationPageInspectorTests {

	@Test
	void detectsLoginFormAndChallengeStates() {
		assertThat(InstagramAuthenticationPageInspector.inspect(
				"https://www.instagram.com/accounts/login/",
				"<form action='/accounts/login/'><input name='username'><input name='password'></form>"
		)).isEqualTo(InstagramAuthenticationStatus.AUTH_REQUIRED);

		assertThat(InstagramAuthenticationPageInspector.inspect(
				"https://www.instagram.com/challenge/",
				"<main>Confirm it is you</main>"
		)).isEqualTo(InstagramAuthenticationStatus.CHALLENGE_REQUIRED);
	}

	@Test
	void detectsAuthenticatedNavigationStructurally() {
		assertThat(InstagramAuthenticationPageInspector.inspect(
				"https://www.instagram.com/",
				"<nav><a href='/'>Home</a><a href='/explore/'>Explore</a></nav><main><article><time datetime='2026-07-14T10:30:00.000Z'></time></article></main>"
		)).isEqualTo(InstagramAuthenticationStatus.AUTHENTICATED);
	}

	@Test
	void detectsRateLimitAndRestriction() {
		assertThat(InstagramAuthenticationPageInspector.inspect(
				"https://www.instagram.com/",
				"<main>Please wait a few minutes before you try again.</main>"
		)).isEqualTo(InstagramAuthenticationStatus.RATE_LIMITED);

		assertThat(InstagramAuthenticationPageInspector.inspect(
				"https://www.instagram.com/",
				"<main>Your account has been disabled.</main>"
		)).isEqualTo(InstagramAuthenticationStatus.ACCOUNT_RESTRICTED);
	}
}
