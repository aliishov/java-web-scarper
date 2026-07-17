package org.raul.javawebscarper.scraper.adapter.facebook;

public enum FacebookAuthenticationStatus {
	AUTHENTICATED,
	AUTH_REQUIRED,
	AUTH_STATE_EXPIRED,
	CHALLENGE_REQUIRED,
	CHECKPOINT_REQUIRED,
	RATE_LIMITED,
	ACCOUNT_RESTRICTED,
	UNKNOWN
}
