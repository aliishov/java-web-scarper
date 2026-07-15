package org.raul.javawebscarper.scraper.adapter.xcom;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Locale;

public final class XAuthenticationPageInspector {

	private static final String AUTHENTICATED_NAVIGATION = String.join(", ",
			"a[href='/home']",
			"a[data-testid='AppTabBar_Home_Link']",
			"a[href='/compose/post']",
			"[data-testid='SideNav_AccountSwitcher_Button']",
			"[data-testid='AppTabBar_Profile_Link']"
	);

	private XAuthenticationPageInspector() {
	}

	public static XAuthenticationStatus inspect(String url, String html) {
		Document document = Jsoup.parse(html == null ? "" : html);
		String normalizedUrl = normalize(url);
		String normalizedText = normalize(document.text());

		if (containsRateLimit(normalizedText)) {
			return XAuthenticationStatus.RATE_LIMITED;
		}
		if (containsChallenge(normalizedText, document)) {
			return XAuthenticationStatus.CHALLENGE_REQUIRED;
		}
		if (isLoginRedirect(normalizedUrl)) {
			return XAuthenticationStatus.AUTH_REQUIRED;
		}
		if (hasLoginForm(document) || containsLoginWall(normalizedText)) {
			return XAuthenticationStatus.AUTH_STATE_EXPIRED;
		}
		if (isAuthenticatedPage(normalizedUrl, document)) {
			return XAuthenticationStatus.AUTHENTICATED;
		}
		if (!document.select(XComSelectors.TWEET_ARTICLE).isEmpty() && !containsLoginWall(normalizedText)) {
			return XAuthenticationStatus.AUTHENTICATED;
		}
		return XAuthenticationStatus.UNKNOWN;
	}

	public static boolean isAuthenticated(String url, String html) {
		return inspect(url, html) == XAuthenticationStatus.AUTHENTICATED;
	}

	private static boolean isAuthenticatedPage(String normalizedUrl, Document document) {
		return normalizedUrl.contains("/home") || document.selectFirst(AUTHENTICATED_NAVIGATION) != null;
	}

	private static boolean isLoginRedirect(String normalizedUrl) {
		return normalizedUrl.contains("/i/flow/login") || normalizedUrl.endsWith("/login") || normalizedUrl.contains("/login?");
	}

	private static boolean hasLoginForm(Document document) {
		return document.selectFirst(XComSelectors.LOGIN_INPUT + ", input[type='password']") != null;
	}

	private static boolean containsLoginWall(String normalizedText) {
		return normalizedText.contains("sign in to x")
				|| normalizedText.contains("log in to x")
				|| normalizedText.contains("create your account")
				|| normalizedText.contains("sign up for x")
				|| normalizedText.contains("войдите в x")
				|| normalizedText.contains("зарегистрируйтесь")
				|| normalizedText.contains("x-ə daxil olun")
				|| normalizedText.contains("x hesabına daxil ol");
	}

	private static boolean containsRateLimit(String normalizedText) {
		return normalizedText.contains("rate limit")
				|| normalizedText.contains("too many requests")
				|| normalizedText.contains("try again later");
	}

	private static boolean containsChallenge(String normalizedText, Document document) {
		return normalizedText.contains("verify your identity")
				|| normalizedText.contains("confirmation code")
				|| normalizedText.contains("enter your code")
				|| normalizedText.contains("suspicious login")
				|| normalizedText.contains("unusual login")
				|| normalizedText.contains("captcha")
				|| document.selectFirst("iframe[src*='captcha'], iframe[title*='captcha' i], input[name*='challenge' i]") != null;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
