package org.raul.javawebscarper.scraper.adapter.xcom;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XComScraperSupport {

	public static final String SOURCE_CODE = "X_COM";
	public static final String BASE_URL = "https://x.com/";
	private static final Set<String> SUPPORTED_CODES = Set.of("X_COM", "TWITTER", "TWITTER_X", "X");
	private static final Pattern STATUS_PATH = Pattern.compile("^/([^/]+)/status/(\\d+)(?:/.*)?$", Pattern.CASE_INSENSITIVE);
	private static final Set<String> SUPPORTED_LANGUAGES = Set.of("az", "ru", "en", "tr");

	private XComScraperSupport() {
	}

	public static boolean supports(Source source) {
		if (source == null) {
			return false;
		}
		String code = source.getCode();
		if (code != null && SUPPORTED_CODES.contains(code.trim().toUpperCase(Locale.ROOT))) {
			return true;
		}
		String baseUrl = source.getBaseUrl();
		if (baseUrl == null) {
			return false;
		}
		String normalized = baseUrl.toLowerCase(Locale.ROOT);
		return normalized.contains("x.com") || normalized.contains("twitter.com");
	}

	public static Optional<XStatusUrl> parseStatusUrl(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalizedUrl;
		try {
			normalizedUrl = UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(BASE_URL, value));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		URI uri;
		try {
			uri = URI.create(removeFragment(normalizedUrl));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		String host = uri.getHost();
		String path = uri.getPath();
		if (host == null || path == null || isBlockedHost(host)) {
			return Optional.empty();
		}
		Matcher matcher = STATUS_PATH.matcher(path);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		String username = matcher.group(1);
		String statusId = matcher.group(2);
		if (username == null || username.isBlank() || statusId == null || statusId.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new XStatusUrl(
				statusId,
				"https://x.com/" + username + "/status/" + statusId,
				username
		));
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("blob:")
				&& !normalized.startsWith("data:")
				&& !normalized.contains("profile_images")
				&& !normalized.contains("emoji")
				&& !normalized.contains("abs.twimg.com")
				&& !normalized.contains("pbs.twimg.com/profile_images");
	}

	public static String normalizeLanguage(String value, String fallback) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (SUPPORTED_LANGUAGES.contains(normalized)) {
			return normalized;
		}
		String fallbackNormalized = fallback == null ? "" : fallback.trim().toLowerCase(Locale.ROOT);
		return SUPPORTED_LANGUAGES.contains(fallbackNormalized) ? fallbackNormalized : null;
	}

	public static boolean isPromotedText(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		String normalized = text.toLowerCase(Locale.ROOT);
		return normalized.contains("promoted")
				|| normalized.contains("sponsored")
				|| normalized.contains("advertisement")
				|| normalized.contains("реклама")
				|| normalized.contains("продвигается")
				|| normalized.contains("reklam")
				|| normalized.contains("sponsorlu");
	}

	private static boolean isBlockedHost(String host) {
		String normalized = host.toLowerCase(Locale.ROOT);
		return !normalized.equals("x.com")
				&& !normalized.endsWith(".x.com")
				&& !normalized.equals("twitter.com")
				&& !normalized.endsWith(".twitter.com");
	}

	private static String removeFragment(String url) {
		int fragmentIndex = url.indexOf('#');
		return fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
	}
}
