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
		if (url == null || url.isBlank()) {
			return null;
		}
		String normalizedUrl;
		try {
			normalizedUrl = removeFragment(UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(BASE_URL, url)));
		} catch (IllegalArgumentException exception) {
			return null;
		}
		return isPostUrl(normalizedUrl) ? normalizedUrl : null;
	}

	public static Optional<String> extractExternalPostId(String postUrl) {
		return extractExternalPostId(null, postUrl);
	}

	public static Optional<String> extractExternalPostId(String dataPage, String postUrl) {
		if (dataPage != null && dataPage.trim().matches("\\d+")) {
			return Optional.of(dataPage.trim());
		}
		if (postUrl == null || postUrl.isBlank()) {
			return Optional.empty();
		}
		String normalizedUrl = normalizePostUrl(postUrl);
		if (normalizedUrl == null) {
			return Optional.empty();
		}
		URI uri = URI.create(normalizedUrl);
		String path = uri.getPath();
		if (path == null || path.isBlank() || "/".equals(path)) {
			return Optional.empty();
		}
		String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		int lastSlashIndex = normalizedPath.lastIndexOf('/');
		String lastSegment = lastSlashIndex >= 0 ? normalizedPath.substring(lastSlashIndex + 1) : normalizedPath;
		return lastSegment.isBlank() ? Optional.empty() : Optional.of(lastSegment);
	}

	public static boolean isPostUrl(String url) {
		if (url == null || url.isBlank() || isBlockedUrl(url)) {
			return false;
		}
		URI uri;
		try {
			uri = URI.create(url.trim());
		} catch (IllegalArgumentException exception) {
			return false;
		}
		String host = uri.getHost();
		String path = uri.getPath();
		if (host == null || path == null || path.isBlank() || "/".equals(path)) {
			return false;
		}
		String normalizedHost = host.toLowerCase(Locale.ROOT);
		if (!normalizedHost.equals("baku.ws") && !normalizedHost.endsWith(".baku.ws")) {
			return false;
		}
		String normalizedPath = path.toLowerCase(Locale.ROOT);
		if (normalizedPath.equals("/search")
				|| normalizedPath.startsWith("/search/")
				|| normalizedPath.equals("/tag")
				|| normalizedPath.startsWith("/tag/")
				|| normalizedPath.startsWith("/storage/")
				|| normalizedPath.startsWith("/images/")
				|| normalizedPath.startsWith("/assets/")) {
			return false;
		}
		if (normalizedPath.matches(".*\\.(webp|png|jpe?g|gif|svg|mp4|webm|mp3|pdf)$")) {
			return false;
		}
		String[] segments = normalizedPath.split("/");
		int nonBlankSegments = 0;
		for (String segment : segments) {
			if (!segment.isBlank()) {
				nonBlankSegments++;
			}
		}
		return nonBlankSegments >= 2;
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("data:image")
				&& !normalized.contains("yandex")
				&& !normalized.contains("avatars.mds")
				&& !normalized.contains("/images/banners/")
				&& !normalized.contains("/assets/banners/")
				&& !normalized.contains("placeholder")
				&& !normalized.contains("placeholder_home")
				&& !normalized.contains("icons-v6.svg")
				&& !normalized.contains("/icons/")
				&& !normalized.contains("icon")
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

	private static boolean isBlockedUrl(String url) {
		String normalized = url.toLowerCase(Locale.ROOT);
		return normalized.startsWith("mailto:")
				|| normalized.startsWith("javascript:")
				|| normalized.contains("yandex")
				|| normalized.contains("telegram")
				|| normalized.contains("facebook")
				|| normalized.contains("whatsapp")
				|| normalized.contains("/images/banners/")
				|| normalized.contains("placeholder_home")
				|| normalized.contains("banner");
	}

	private static String removeFragment(String url) {
		int fragmentIndex = url.indexOf('#');
		return fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
	}
}
