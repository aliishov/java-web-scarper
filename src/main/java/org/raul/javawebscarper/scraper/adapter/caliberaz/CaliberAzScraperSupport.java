package org.raul.javawebscarper.scraper.adapter.caliberaz;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CaliberAzScraperSupport {

	public static final String SOURCE_CODE = "CALIBER_AZ";
	public static final String BASE_URL = "https://caliber.az/";

	private static final Pattern BACKGROUND_IMAGE_PATTERN = Pattern.compile(
			"url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)",
			Pattern.CASE_INSENSITIVE
	);

	private CaliberAzScraperSupport() {
	}

	public static boolean supports(Source source) {
		if (source == null) {
			return false;
		}
		String code = source.getCode();
		if (code != null && SOURCE_CODE.equalsIgnoreCase(code.trim())) {
			return true;
		}
		String baseUrl = source.getBaseUrl();
		return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("caliber.az");
	}

	public static Optional<String> normalizePostUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		String normalizedUrl;
		try {
			normalizedUrl = UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(BASE_URL, rawUrl));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		if (!isPostUrl(normalizedUrl)) {
			return Optional.empty();
		}
		return Optional.of(removeFragment(normalizedUrl));
	}

	public static boolean isPostUrl(String url) {
		if (url == null || url.isBlank()) {
			return false;
		}
		URI uri;
		try {
			uri = URI.create(UrlNormalizer.resolve(BASE_URL, url));
		} catch (IllegalArgumentException exception) {
			return false;
		}
		String host = uri.getHost();
		String path = uri.getPath();
		if (host == null || path == null) {
			return false;
		}
		String normalizedHost = host.toLowerCase(Locale.ROOT);
		String normalizedPath = path.toLowerCase(Locale.ROOT);
		return (normalizedHost.equals("caliber.az") || normalizedHost.endsWith(".caliber.az"))
				&& normalizedPath.startsWith("/post/")
				&& normalizedPath.length() > "/post/".length();
	}

	public static Optional<String> extractExternalPostId(String postUrl) {
		if (postUrl == null || postUrl.isBlank()) {
			return Optional.empty();
		}
		Optional<String> normalizedUrl = normalizePostUrl(postUrl);
		if (normalizedUrl.isEmpty()) {
			return Optional.empty();
		}
		String path = URI.create(normalizedUrl.get()).getPath();
		String slug = path.substring("/post/".length());
		if (slug.endsWith("/")) {
			slug = slug.substring(0, slug.length() - 1);
		}
		return slug.isBlank() ? Optional.empty() : Optional.of(slug);
	}

	public static Optional<String> extractBackgroundImageUrl(String style) {
		if (style == null || style.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = BACKGROUND_IMAGE_PATTERN.matcher(style.trim());
		if (!matcher.find()) {
			return Optional.empty();
		}
		String url = matcher.group(1);
		return url == null || url.isBlank() ? Optional.empty() : Optional.of(url.trim());
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("data:image")
				&& !normalized.contains("banner")
				&& !normalized.contains("advert")
				&& !normalized.contains("reklam")
				&& !normalized.contains("logo")
				&& !normalized.contains("template")
				&& !normalized.contains("zoom.png")
				&& !normalized.contains("sprite")
				&& !normalized.contains("icon")
				&& !normalized.contains("doubleclick")
				&& !normalized.contains("googleads")
				&& !normalized.endsWith(".svg");
	}

	private static String removeFragment(String url) {
		int fragmentIndex = url.indexOf('#');
		return fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
	}
}
