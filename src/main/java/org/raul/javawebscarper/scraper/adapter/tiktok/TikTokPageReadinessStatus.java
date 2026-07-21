package org.raul.javawebscarper.scraper.adapter.tiktok;

public enum TikTokPageReadinessStatus {
	READY,
	LOADING,
	LOGIN_MODAL,
	COOKIE_CONSENT,
	CAPTCHA,
	VERIFICATION,
	RATE_LIMITED,
	ERROR_PAGE,
	UNKNOWN
}
