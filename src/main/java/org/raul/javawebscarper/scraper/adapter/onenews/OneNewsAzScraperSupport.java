package org.raul.javawebscarper.scraper.adapter.onenews;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OneNewsAzScraperSupport {

	public static final String SOURCE_CODE = "ONE_NEWS_AZ";
	public static final String BASE_URL = "https://1news.az/az";
	public static final String ROOT_URL = "https://1news.az";

	private static final Pattern EXTERNAL_ID_PATTERN = Pattern.compile("/az/news/(\\d+)(?:-|$)");

	private OneNewsAzScraperSupport() {
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
		return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("1news.az");
	}

	public static String normalizePostUrl(String url) {
		return UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(ROOT_URL, url));
	}

	public static Optional<String> extractExternalPostId(String postUrl) {
		if (postUrl == null || postUrl.isBlank()) {
			return Optional.empty();
		}
		String normalizedUrl = normalizePostUrl(postUrl);
		Matcher matcher = EXTERNAL_ID_PATTERN.matcher(normalizedUrl);
		if (matcher.find()) {
			return Optional.of(matcher.group(1));
		}
		URI uri = URI.create(normalizedUrl);
		String path = uri.getPath();
		if (path == null || path.isBlank() || "/".equals(path)) {
			return Optional.empty();
		}
		String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		int lastSlashIndex = normalizedPath.lastIndexOf('/');
		String slug = lastSlashIndex >= 0 ? normalizedPath.substring(lastSlashIndex + 1) : normalizedPath;
		return slug.isBlank() ? Optional.empty() : Optional.of(slug);
	}

	public static boolean isArticleUrl(String url) {
		if (url == null || url.isBlank()) {
			return false;
		}
		String normalizedUrl = normalizePostUrl(url);
		URI uri;
		try {
			uri = URI.create(normalizedUrl);
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
		return (normalizedHost.equals("1news.az") || normalizedHost.endsWith(".1news.az"))
				&& normalizedPath.startsWith("/az/news/")
				&& EXTERNAL_ID_PATTERN.matcher(path).find();
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("data:image")
				&& !normalized.contains("adviad")
				&& !normalized.contains("banner")
				&& !normalized.contains("advert")
				&& !normalized.contains("reklam")
				&& !normalized.contains("doubleclick")
				&& !normalized.contains("googleads")
				&& !normalized.contains("yandex")
				&& !normalized.contains("social")
				&& !normalized.contains("icon")
				&& !normalized.contains("logo")
				&& !normalized.endsWith(".svg");
	}
}
