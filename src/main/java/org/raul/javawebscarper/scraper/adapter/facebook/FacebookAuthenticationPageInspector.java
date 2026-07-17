package org.raul.javawebscarper.scraper.adapter.facebook;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Locale;

public final class FacebookAuthenticationPageInspector {

	private FacebookAuthenticationPageInspector() {
	}

	public static FacebookAuthenticationStatus inspect(String url, String html) {
		Document document = Jsoup.parse(html == null ? "" : html);
		String normalizedUrl = normalize(url);
		String normalizedText = normalize(document.text());

		if (containsTemporaryBlock(normalizedText)) {
			return FacebookAuthenticationStatus.TEMPORARILY_BLOCKED;
		}
		if (containsAccountRestriction(normalizedText)) {
			return FacebookAuthenticationStatus.ACCOUNT_RESTRICTED;
		}
		if (containsRateLimit(normalizedText)) {
			return FacebookAuthenticationStatus.RATE_LIMITED;
		}
		if (containsCheckpoint(normalizedUrl, normalizedText)) {
			return FacebookAuthenticationStatus.CHECKPOINT_REQUIRED;
		}
		if (containsTwoFactor(normalizedUrl, normalizedText, document)) {
			return FacebookAuthenticationStatus.TWO_FACTOR_REQUIRED;
		}
		if (containsChallenge(normalizedText, document)) {
			return FacebookAuthenticationStatus.CHALLENGE_REQUIRED;
		}
		if (isLoginRedirect(normalizedUrl)) {
			return FacebookAuthenticationStatus.AUTH_REQUIRED;
		}
		if (hasLoginForm(document) || containsLoginWall(normalizedText) || containsSessionExpired(normalizedText)) {
			return FacebookAuthenticationStatus.AUTH_STATE_EXPIRED;
		}
		if (isAuthenticatedPage(normalizedUrl, document)) {
			return FacebookAuthenticationStatus.AUTHENTICATED;
		}
		return FacebookAuthenticationStatus.UNKNOWN;
	}

	public static boolean isAuthenticated(String url, String html) {
		return inspect(url, html) == FacebookAuthenticationStatus.AUTHENTICATED;
	}

	private static boolean isAuthenticatedPage(String normalizedUrl, Document document) {
		return normalizedUrl.contains("facebook.com")
				&& !normalizedUrl.contains("/login")
				&& !normalizedUrl.contains("/checkpoint")
				&& !hasLoginForm(document)
				&& document.selectFirst(FacebookSelectors.AUTHENTICATED_NAVIGATION) != null
				&& document.selectFirst(FacebookSelectors.SEARCH_INPUT) != null;
	}

	private static boolean isLoginRedirect(String normalizedUrl) {
		return normalizedUrl.contains("/login")
				|| normalizedUrl.contains("/login/identify")
				|| normalizedUrl.contains("/recover/initiate")
				|| normalizedUrl.contains("/reg/");
	}

	private static boolean hasLoginForm(Document document) {
		return document.selectFirst(FacebookSelectors.LOGIN_INPUT + ", " + FacebookSelectors.PASSWORD_INPUT) != null;
	}

	private static boolean containsLoginWall(String normalizedText) {
		return normalizedText.contains("log into facebook")
				|| normalizedText.contains("log in to facebook")
				|| normalizedText.contains("facebook-a daxil ol")
				|| normalizedText.contains("daxil ol")
				|| normalizedText.contains("giris yap")
				|| normalizedText.contains("giriş yap")
				|| normalizedText.contains("create new account");
	}

	private static boolean containsSessionExpired(String normalizedText) {
		return normalizedText.contains("session expired")
				|| normalizedText.contains("please log in again")
				|| normalizedText.contains("log in again");
	}

	private static boolean containsCheckpoint(String normalizedUrl, String normalizedText) {
		return normalizedUrl.contains("/checkpoint/")
				|| normalizedText.contains("checkpoint")
				|| normalizedText.contains("security check");
	}

	private static boolean containsTwoFactor(String normalizedUrl, String normalizedText, Document document) {
		return normalizedUrl.contains("/two_step_verification/")
				|| normalizedUrl.contains("/two_factor/")
				|| normalizedText.contains("two-factor")
				|| normalizedText.contains("two factor")
				|| normalizedText.contains("two-step verification")
				|| normalizedText.contains("authentication code")
				|| document.selectFirst("input[name*='approvals_code' i], input[autocomplete='one-time-code']") != null;
	}

	private static boolean containsChallenge(String normalizedText, Document document) {
		return normalizedText.contains("captcha")
				|| normalizedText.contains("confirm your identity")
				|| normalizedText.contains("suspicious login")
				|| normalizedText.contains("unusual login")
				|| document.selectFirst("iframe[src*='captcha'], iframe[title*='captcha' i]") != null;
	}

	private static boolean containsRateLimit(String normalizedText) {
		return normalizedText.contains("rate limit")
				|| normalizedText.contains("too many requests")
				|| normalizedText.contains("too many actions");
	}

	private static boolean containsTemporaryBlock(String normalizedText) {
		return normalizedText.contains("you're temporarily blocked")
				|| normalizedText.contains("you are temporarily blocked")
				|| normalizedText.contains("temporarily blocked");
	}

	private static boolean containsAccountRestriction(String normalizedText) {
		return normalizedText.contains("account restricted")
				|| normalizedText.contains("your account has been restricted");
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
