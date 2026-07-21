package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.raul.javawebscarper.browser.BrowserPage;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TikTokPageReadinessVerifier {

	public TikTokPageReadinessStatus verify(BrowserPage page) {
		return inspect(page.url(), page.content());
	}

	public static TikTokPageReadinessStatus inspect(String url, String html) {
		String safeUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
		Document document = Jsoup.parse(html == null ? "" : html);
		String text = document.text().toLowerCase(Locale.ROOT);

		if (hasCaptcha(document, text)) {
			return TikTokPageReadinessStatus.CAPTCHA;
		}
		if (hasRateLimit(text)) {
			return TikTokPageReadinessStatus.RATE_LIMITED;
		}
		if (hasVerification(safeUrl, document, text)) {
			return TikTokPageReadinessStatus.VERIFICATION;
		}
		if (hasErrorPage(safeUrl, text)) {
			return TikTokPageReadinessStatus.ERROR_PAGE;
		}
		if (hasLoginModal(safeUrl, document, text)) {
			return TikTokPageReadinessStatus.LOGIN_MODAL;
		}
		if (hasCookieConsent(document, text)) {
			return TikTokPageReadinessStatus.COOKIE_CONSENT;
		}
		if (isReady(document)) {
			return TikTokPageReadinessStatus.READY;
		}
		if (document.select("body").isEmpty() || text.isBlank() || text.contains("loading")) {
			return TikTokPageReadinessStatus.LOADING;
		}
		return TikTokPageReadinessStatus.UNKNOWN;
	}

	static boolean hasCaptcha(Document document, String text) {
		return text.contains("captcha")
				|| text.contains("verify you are human")
				|| text.contains("robot")
				|| text.contains("slide to verify")
				|| text.contains("drag the slider")
				|| document.select("iframe[src*=captcha], iframe[src*=challenge], [id*=captcha], [class*=captcha], [data-e2e*=captcha]").size() > 0;
	}

	static boolean hasVerification(String url, Document document, String text) {
		return url.contains("/verify")
				|| url.contains("/checkpoint")
				|| text.contains("security verification")
				|| text.contains("verification code")
				|| text.contains("verify your account")
				|| text.contains("подтвердите")
				|| text.contains("təhlükəsizlik yoxlaması")
				|| document.select("input[autocomplete=one-time-code], input[name*=verify], input[name*=code]").size() > 0;
	}

	static boolean hasRateLimit(String text) {
		return text.contains("too many attempts")
				|| text.contains("try again later")
				|| text.contains("temporarily blocked")
				|| text.contains("temporarily unavailable")
				|| text.contains("rate limit")
				|| text.contains("access too frequent")
				|| text.contains("слишком много попыток");
	}

	static boolean hasLoginModal(String url, Document document, String text) {
		boolean loginForm = document.select("form[action*=login], input[name=username], input[type=password]").size() >= 2;
		boolean modal = document.select("[data-e2e*=login], [id*=login-modal], [class*=login-modal], [role=dialog]").stream()
				.anyMatch(element -> element.text().toLowerCase(Locale.ROOT).contains("log in")
						|| element.text().toLowerCase(Locale.ROOT).contains("sign up")
						|| element.text().toLowerCase(Locale.ROOT).contains("войти"));
		return url.contains("/login")
				|| loginForm
				|| modal
				|| text.contains("log in to tiktok")
				|| text.contains("sign up for tiktok")
				|| text.contains("войдите в tiktok");
	}

	static boolean hasCookieConsent(Document document, String text) {
		return text.contains("accept all cookies")
				|| text.contains("allow all cookies")
				|| text.contains("manage cookies")
				|| text.contains("cookies")
				&& document.select("button").stream()
				.anyMatch(button -> button.text().toLowerCase(Locale.ROOT).contains("accept")
						|| button.text().toLowerCase(Locale.ROOT).contains("allow"));
	}

	private static boolean hasErrorPage(String url, String text) {
		return url.contains("/404")
				|| text.contains("couldn't find this page")
				|| text.contains("page not available")
				|| text.contains("video currently unavailable")
				|| text.contains("this video is unavailable");
	}

	private static boolean isReady(Document document) {
		return document.select("main, #app, [data-e2e], input[type=search], [role=search], a[href*='/video/'], video").size() > 0;
	}
}
