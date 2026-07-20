package org.raul.javawebscarper.scraper.adapter.instagram;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Locale;

public final class InstagramAuthenticationPageInspector {

	private InstagramAuthenticationPageInspector() {
	}

	public static InstagramAuthenticationStatus inspect(String url, String html) {
		String safeUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
		Document document = Jsoup.parse(html == null ? "" : html);
		String text = document.text().toLowerCase(Locale.ROOT);

		if (safeUrl.contains("/challenge/") || safeUrl.contains("/checkpoint/") || text.contains("challenge required")) {
			return InstagramAuthenticationStatus.CHALLENGE_REQUIRED;
		}
		if (safeUrl.contains("/two_factor") || safeUrl.contains("/two-factor")) {
			return InstagramAuthenticationStatus.TWO_FACTOR_REQUIRED;
		}
		if (text.contains("two-factor") || text.contains("two factor") || text.contains("security code")
				|| document.select("input[name=verificationCode], input[autocomplete=one-time-code]").size() > 0) {
			return InstagramAuthenticationStatus.TWO_FACTOR_REQUIRED;
		}
		if (text.contains("suspicious login") || text.contains("suspicious attempt")) {
			return InstagramAuthenticationStatus.SUSPICIOUS_LOGIN;
		}
		if (text.contains("temporarily blocked")) {
			return InstagramAuthenticationStatus.TEMPORARILY_BLOCKED;
		}
		if (text.contains("try again later") || text.contains("wait a few minutes") || text.contains("rate limit")) {
			return InstagramAuthenticationStatus.RATE_LIMITED;
		}
		if (text.contains("account restricted") || text.contains("account disabled") || text.contains("account has been disabled")) {
			return InstagramAuthenticationStatus.ACCOUNT_RESTRICTED;
		}
		if (safeUrl.contains("/privacy/consent") || text.contains("allow instagram to use cookies")
				|| text.contains("save your login info") || text.contains("сохранить данные для входа")) {
			return InstagramAuthenticationStatus.CONSENT_REQUIRED;
		}
		boolean loginForm = document.select("form[action*='/accounts/login'], input[name=username], input[name=password]").size() >= 2;
		boolean loginLanding = text.contains("log in to instagram")
				|| text.contains("войти в instagram")
				|| text.contains("instagram-a daxil ol")
				|| text.contains("instagram'a giriş yap");
		if (safeUrl.contains("/accounts/login") || loginForm || loginLanding) {
			return InstagramAuthenticationStatus.AUTH_REQUIRED;
		}
		boolean authenticatedNavigation = document.select("nav a[href='/'], nav a[href^='/explore/'], a[href^='/direct/inbox/'], input[type=search], input[role=combobox], input[placeholder=Search], input[aria-label=Search]").size() > 0;
		boolean postOrExplorePage = document.select("a[href^='/p/'], a[href^='/reel/'], article time[datetime], main article").size() > 0;
		if (!safeUrl.contains("/accounts/login") && !loginForm && (authenticatedNavigation || postOrExplorePage)) {
			return InstagramAuthenticationStatus.AUTHENTICATED;
		}
		return InstagramAuthenticationStatus.UNKNOWN;
	}
}
