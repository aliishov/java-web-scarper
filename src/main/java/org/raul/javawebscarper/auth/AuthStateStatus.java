package org.raul.javawebscarper.auth;

public enum AuthStateStatus {
	DISABLED,
	MISSING,
	VALID,
	EXPIRED,
	LOGIN_FAILED,
	CHALLENGE_REQUIRED,
	AUTH_REQUIRED
}
