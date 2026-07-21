package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.raul.javawebscarper.browser.BrowserPage;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TikTokAuthenticationVerifier {

	public TikTokAuthenticationStatus verify(BrowserPage page) {
		return inspect(page.url(), page.content());
	}

	public static TikTokAuthenticationStatus inspect(String url, String html) {
		String safeUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
		Document document = Jsoup.parse(html == null ? "" : html);
		String text = document.text().toLowerCase(Locale.ROOT);
		TikTokPageReadinessStatus readiness = TikTokPageReadinessVerifier.inspect(url, html);
		return switch (readiness) {
			case CAPTCHA -> TikTokAuthenticationStatus.CAPTCHA_REQUIRED;
			case VERIFICATION -> TikTokAuthenticationStatus.VERIFICATION_REQUIRED;
			case RATE_LIMITED -> text.contains("temporarily blocked")
					? TikTokAuthenticationStatus.TEMPORARILY_BLOCKED
					: TikTokAuthenticationStatus.RATE_LIMITED;
			case ERROR_PAGE -> TikTokAuthenticationStatus.UNKNOWN;
			case LOGIN_MODAL -> TikTokAuthenticationStatus.AUTH_REQUIRED;
			default -> {
				if (text.contains("account restricted") || text.contains("account disabled")) {
					yield TikTokAuthenticationStatus.ACCOUNT_RESTRICTED;
				}
				if (safeUrl.contains("/login") || hasLoginForm(document)) {
					yield TikTokAuthenticationStatus.AUTH_REQUIRED;
				}
				if (hasAuthenticatedMarker(document)) {
					yield TikTokAuthenticationStatus.AUTHENTICATED;
				}
				if (hasAnonymousAccess(document, safeUrl)) {
					yield TikTokAuthenticationStatus.ANONYMOUS_ACCESS;
				}
				yield TikTokAuthenticationStatus.UNKNOWN;
			}
		};
	}

	private static boolean hasLoginForm(Document document) {
		return document.select("form[action*=login], input[name=username], input[type=password]").size() >= 2;
	}

	private static boolean hasAuthenticatedMarker(Document document) {
		return document.select("[data-e2e*=profile-icon], [data-e2e=nav-profile], a[href^='/@'][data-e2e*=profile], button[aria-label*=Profile]").size() > 0;
	}

	private static boolean hasAnonymousAccess(Document document, String url) {
		return document.select("input[type=search], [role=search], a[href*='/video/'], video, main").size() > 0
				&& !url.contains("/login");
	}
}
