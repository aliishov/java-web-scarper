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

		if (containsRateLimit(normalizedText)) {
			return FacebookAuthenticationStatus.RATE_LIMITED;
		}
		if (containsAccountRestriction(normalizedText)) {
			return FacebookAuthenticationStatus.ACCOUNT_RESTRICTED;
		}
		if (containsCheckpoint(normalizedUrl, normalizedText)) {
			return FacebookAuthenticationStatus.CHECKPOINT_REQUIRED;
		}
		if (containsChallenge(normalizedText, document)) {
			return FacebookAuthenticationStatus.CHALLENGE_REQUIRED;
		}
		if (isLoginRedirect(normalizedUrl)) {
			return FacebookAuthenticationStatus.AUTH_REQUIRED;
		}
		if (hasLoginForm(document) || containsLoginWall(normalizedText)) {
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
				&& document.selectFirst(FacebookSelectors.AUTHENTICATED_NAVIGATION) != null;
	}

	private static boolean isLoginRedirect(String normalizedUrl) {
		return normalizedUrl.contains("/login")
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
				|| normalizedText.contains("войдите на facebook")
				|| normalizedText.contains("войдите в facebook")
				|| normalizedText.contains("create new account")
				|| normalizedText.contains("создать аккаунт");
	}

	private static boolean containsCheckpoint(String normalizedUrl, String normalizedText) {
		return normalizedUrl.contains("/checkpoint/")
				|| normalizedText.contains("checkpoint")
				|| normalizedText.contains("security check")
				|| normalizedText.contains("проверка безопасности");
	}

	private static boolean containsChallenge(String normalizedText, Document document) {
		return normalizedText.contains("enter the code")
				|| normalizedText.contains("two-factor")
				|| normalizedText.contains("two factor")
				|| normalizedText.contains("captcha")
				|| normalizedText.contains("confirm your identity")
				|| normalizedText.contains("подтвердите личность")
				|| document.selectFirst("iframe[src*='captcha'], iframe[title*='captcha' i], input[name*='approvals_code' i]") != null;
	}

	private static boolean containsRateLimit(String normalizedText) {
		return normalizedText.contains("you’re temporarily blocked")
				|| normalizedText.contains("you're temporarily blocked")
				|| normalizedText.contains("try again later")
				|| normalizedText.contains("too many requests")
				|| normalizedText.contains("temporarily blocked")
				|| normalizedText.contains("временно заблокировано");
	}

	private static boolean containsAccountRestriction(String normalizedText) {
		return normalizedText.contains("account restricted")
				|| normalizedText.contains("your account has been restricted")
				|| normalizedText.contains("аккаунт ограничен");
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
