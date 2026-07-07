package org.raul.javawebscarper.scraper.adapter.bakuws;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

public final class BakuWsScraperSupport {

	public static final String SOURCE_CODE = "BAKU_WS";
	public static final String BASE_URL = "https://baku.ws/";

	private BakuWsScraperSupport() {
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
		return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("baku.ws");
	}

	public static String normalizePostUrl(String url) {
		return UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(BASE_URL, url));
	}

	public static Optional<String> extractExternalPostId(String postUrl) {
		if (postUrl == null || postUrl.isBlank()) {
			return Optional.empty();
		}
		URI uri = URI.create(postUrl.trim());
		String path = uri.getPath();
		if (path == null || path.isBlank() || "/".equals(path)) {
			return Optional.empty();
		}
		String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		int lastSlashIndex = normalizedPath.lastIndexOf('/');
		String lastSegment = lastSlashIndex >= 0 ? normalizedPath.substring(lastSlashIndex + 1) : normalizedPath;
		return lastSegment.isBlank() ? Optional.empty() : Optional.of(lastSegment);
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("data:image")
				&& !normalized.contains("/images/banners/")
				&& !normalized.contains("/assets/banners/")
				&& !normalized.contains("placeholder")
				&& !normalized.contains("placeholder_home")
				&& !normalized.contains("icons-v6.svg")
				&& !normalized.contains("sprite")
				&& !normalized.contains("logo")
				&& !normalized.contains("banner");
	}

	public static MediaType mediaTypeForTag(String tagName) {
		return mediaTypeForTag(tagName, null);
	}

	public static MediaType mediaTypeForTag(String tagName, String parentTagName) {
		if (tagName == null) {
			return MediaType.UNKNOWN;
		}
		String parent = parentTagName == null ? "" : parentTagName.toLowerCase(Locale.ROOT);
		return switch (tagName.toLowerCase(Locale.ROOT)) {
			case "img" -> MediaType.IMAGE;
			case "video" -> MediaType.VIDEO;
			case "source" -> "picture".equals(parent) ? MediaType.IMAGE : MediaType.VIDEO;
			default -> MediaType.UNKNOWN;
		};
	}
}
