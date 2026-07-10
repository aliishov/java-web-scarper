package org.raul.javawebscarper.scraper.adapter.mediaaz;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

public final class MediaAzScraperSupport {

	public static final String SOURCE_CODE = "MEDIA_AZ";
	public static final String BASE_URL = "https://media.az/";

	private MediaAzScraperSupport() {
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
		return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("media.az");
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
				&& !normalized.contains("dsps.newmedia.az")
				&& !normalized.contains("yandex")
				&& !normalized.contains("adservice")
				&& !normalized.contains("doubleclick")
				&& !normalized.contains("googleads")
				&& !normalized.contains("banner")
				&& !normalized.contains("placeholder")
				&& !normalized.contains("sprite")
				&& !normalized.endsWith(".svg")
				&& !normalized.contains("logo")
				&& !normalized.contains("icon");
	}
}
