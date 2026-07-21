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
			case VERIFICATION -> hasTwoFactorChallenge(document, text)
					? TikTokAuthenticationStatus.TWO_FACTOR_REQUIRED
					: TikTokAuthenticationStatus.VERIFICATION_REQUIRED;
			case RATE_LIMITED -> text.contains("temporarily blocked")
					? TikTokAuthenticationStatus.TEMPORARILY_BLOCKED
					: TikTokAuthenticationStatus.RATE_LIMITED;
			case ERROR_PAGE -> TikTokAuthenticationStatus.UNKNOWN;
			case LOGIN_MODAL -> loginModalStatus(safeUrl, document, text);
			default -> {
				if (text.contains("account restricted") || text.contains("account disabled")) {
					yield TikTokAuthenticationStatus.ACCOUNT_RESTRICTED;
				}
				if (hasConsentDialog(safeUrl, document, text)) {
					yield TikTokAuthenticationStatus.CONSENT_REQUIRED;
				}
				if (hasTwoFactorChallenge(document, text)) {
					yield TikTokAuthenticationStatus.TWO_FACTOR_REQUIRED;
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

	private static TikTokAuthenticationStatus loginModalStatus(String url, Document document, String text) {
		if (hasAnonymousAccess(document, url)) {
			return TikTokAuthenticationStatus.ANONYMOUS_ACCESS;
		}
		if (hasTwoFactorChallenge(document, text)) {
			return TikTokAuthenticationStatus.TWO_FACTOR_REQUIRED;
		}
		if (hasConsentDialog(url, document, text)) {
			return TikTokAuthenticationStatus.CONSENT_REQUIRED;
		}
		if (url.contains("/login")) {
			return TikTokAuthenticationStatus.AUTH_REQUIRED;
		}
		return TikTokAuthenticationStatus.LOGIN_MODAL_BLOCKING;
	}

	private static boolean hasLoginForm(Document document) {
		return document.select("form[action*=login], input[name=username], input[type=password]").size() >= 2;
	}

	private static boolean hasTwoFactorChallenge(Document document, String text) {
		return text.contains("two-factor")
				|| text.contains("two factor")
				|| text.contains("2fa")
				|| text.contains("security code")
				|| text.contains("verification code")
				|| document.select("input[autocomplete=one-time-code], input[name*=code], input[name*=otp]").size() > 0;
	}

	private static boolean hasConsentDialog(String url, Document document, String text) {
		return url.contains("/consent")
				|| text.contains("accept cookies")
				|| text.contains("allow all cookies")
				|| text.contains("save your login info")
				|| document.select("[role=dialog]").stream()
				.anyMatch(element -> element.text().toLowerCase(Locale.ROOT).contains("cookies")
						|| element.text().toLowerCase(Locale.ROOT).contains("notification")
						|| element.text().toLowerCase(Locale.ROOT).contains("interest"));
	}

	private static boolean hasAuthenticatedMarker(Document document) {
		return document.select("[data-e2e*=profile-icon], [data-e2e=nav-profile], a[href^='/@'][data-e2e*=profile], button[aria-label*=Profile]").size() > 0;
	}

	private static boolean hasAnonymousAccess(Document document, String url) {
		return document.select("input[type=search], [role=search], a[href*='/video/'], video").size() > 0
				&& !url.contains("/login");
	}
}
