package org.raul.javawebscarper.scraper.adapter.lentaz;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LentAzScraperSupport {

	public static final String SOURCE_CODE = "LENT_AZ";
	public static final String BASE_URL = "https://lent.az/";

	private static final String ARTICLE_PATH_PREFIX = "/xeber/";
	private static final Pattern NUMERIC_SUFFIX = Pattern.compile("-(\\d+)$");

	private LentAzScraperSupport() {
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
		return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("lent.az");
	}

	public static Optional<String> normalizePostUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		String normalizedUrl;
		try {
			normalizedUrl = forceHttps(UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(BASE_URL, rawUrl)));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		normalizedUrl = removeFragment(normalizedUrl);
		if (!isPostUrl(normalizedUrl)) {
			return Optional.empty();
		}
		return Optional.of(normalizedUrl);
	}

	public static boolean isPostUrl(String url) {
		if (url == null || url.isBlank() || isBlockedUrl(url)) {
			return false;
		}
		URI uri;
		try {
			uri = URI.create(forceHttps(UrlNormalizer.resolve(BASE_URL, url)));
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
		return (normalizedHost.equals("lent.az") || normalizedHost.endsWith(".lent.az"))
				&& normalizedPath.startsWith(ARTICLE_PATH_PREFIX)
				&& NUMERIC_SUFFIX.matcher(normalizedPath).find();
	}

	public static Optional<String> extractExternalPostId(String dataId, String postUrl) {
		if (dataId != null && dataId.trim().matches("\\d+")) {
			return Optional.of(dataId.trim());
		}
		Optional<String> normalizedUrl = normalizePostUrl(postUrl);
		if (normalizedUrl.isEmpty()) {
			return Optional.empty();
		}
		String path = URI.create(normalizedUrl.get()).getPath();
		Matcher matcher = NUMERIC_SUFFIX.matcher(path);
		if (matcher.find()) {
			return Optional.of(matcher.group(1));
		}
		String slug = path.substring(path.lastIndexOf('/') + 1);
		return slug.isBlank() ? Optional.empty() : Optional.of(slug);
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("data:")
				&& !normalized.contains("emoji")
				&& !normalized.contains("emoj")
				&& !normalized.contains("eye.svg")
				&& !normalized.contains("arrow_right")
				&& !normalized.contains("desktop-logo")
				&& !normalized.contains("newmedia.az")
				&& !normalized.contains("ads2.")
				&& !normalized.contains("revive")
				&& !normalized.contains("adviad")
				&& !normalized.contains("banner")
				&& !normalized.contains("layihe")
				&& !normalized.contains("logo")
				&& !normalized.contains("sprite")
				&& !normalized.contains("icon")
				&& !normalized.endsWith(".svg");
	}

	public static String forceHttps(String url) {
		if (url == null) {
			return null;
		}
		if (url.startsWith("http://lent.az")) {
			return "https://" + url.substring("http://".length());
		}
		return url;
	}

	private static boolean isBlockedUrl(String url) {
		String normalized = url.toLowerCase(Locale.ROOT);
		return normalized.contains("/layihe")
				|| normalized.contains("newmedia.az")
				|| normalized.contains("ads2.")
				|| normalized.contains("facebook")
				|| normalized.contains("telegram")
				|| normalized.contains("whatsapp")
				|| normalized.startsWith("mailto:")
				|| normalized.startsWith("javascript:");
	}

	private static String removeFragment(String url) {
		int fragmentIndex = url.indexOf('#');
		return fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
	}
}
