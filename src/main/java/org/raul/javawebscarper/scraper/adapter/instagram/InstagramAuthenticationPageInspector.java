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
		if (text.contains("two-factor") || text.contains("two factor") || text.contains("security code")
				|| document.select("input[name=verificationCode], input[autocomplete=one-time-code]").size() > 0) {
			return InstagramAuthenticationStatus.TWO_FACTOR_REQUIRED;
		}
		if (text.contains("suspicious login") || text.contains("suspicious attempt")) {
			return InstagramAuthenticationStatus.SUSPICIOUS_LOGIN;
		}
		if (text.contains("try again later") || text.contains("temporarily blocked") || text.contains("wait a few minutes")
				|| text.contains("rate limit")) {
			return InstagramAuthenticationStatus.RATE_LIMITED;
		}
		if (text.contains("account restricted") || text.contains("account disabled") || text.contains("account has been disabled")) {
			return InstagramAuthenticationStatus.ACCOUNT_RESTRICTED;
		}
		boolean loginForm = document.select("form[action*='/accounts/login'], input[name=username], input[name=password]").size() >= 2;
		if (safeUrl.contains("/accounts/login") || loginForm || text.contains("log in to instagram")) {
			return InstagramAuthenticationStatus.AUTH_REQUIRED;
		}
		boolean authenticatedNavigation = document.select("nav a[href='/'], nav a[href^='/explore/'], a[href^='/direct/inbox/'], input[placeholder=Search], input[aria-label=Search]").size() > 0;
		boolean postOrExplorePage = document.select("a[href^='/p/'], a[href^='/reel/'], article time[datetime], main article").size() > 0;
		if (!safeUrl.contains("/accounts/login") && !loginForm && (authenticatedNavigation || postOrExplorePage)) {
			return InstagramAuthenticationStatus.AUTHENTICATED;
		}
		return InstagramAuthenticationStatus.UNKNOWN;
	}
}
