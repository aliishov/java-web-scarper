package org.raul.javawebscarper.scraper.adapter.haqqinaz;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HaqqinAzScraperSupport {

	public static final String SOURCE_CODE = "HAQQIN_AZ";
	public static final String BASE_URL = "https://haqqin.az/";

	private static final Pattern EXTERNAL_ID_PATTERN = Pattern.compile("/news(?:archive)?/(\\d+)(?:/)?$");

	private HaqqinAzScraperSupport() {
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
		return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("haqqin.az");
	}

	public static Optional<String> normalizeArticleUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		String normalizedUrl;
		try {
			normalizedUrl = UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(BASE_URL, rawUrl));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		if (!isArticleUrl(normalizedUrl)) {
			return Optional.empty();
		}
		return Optional.of(normalizedUrl);
	}

	public static boolean isArticleUrl(String url) {
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
		return (normalizedHost.equals("haqqin.az") || normalizedHost.endsWith(".haqqin.az"))
				&& (normalizedPath.matches("/newsarchive/\\d+/?") || normalizedPath.matches("/news/\\d+/?"));
	}

	public static Optional<String> extractExternalPostId(String postUrl) {
		if (postUrl == null || postUrl.isBlank()) {
			return Optional.empty();
		}
		Optional<String> normalizedUrl = normalizeArticleUrl(postUrl);
		if (normalizedUrl.isEmpty()) {
			return Optional.empty();
		}
		Matcher matcher = EXTERNAL_ID_PATTERN.matcher(URI.create(normalizedUrl.get()).getPath());
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("data:image")
				&& !normalized.contains("banners.haqqin.az")
				&& !normalized.contains("telegram")
				&& !normalized.contains("youtube")
				&& !normalized.contains("whatsapp")
				&& !normalized.contains("banner")
				&& !normalized.contains("advert")
				&& !normalized.contains("reklam")
				&& !normalized.contains("doubleclick")
				&& !normalized.contains("googleads")
				&& !normalized.contains("icon")
				&& !normalized.contains("logo")
				&& !normalized.endsWith(".svg");
	}
}
