package org.raul.javawebscarper.scraper.adapter.onenews;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URLDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
		return extractArticleUrl(url)
				.orElseGet(() -> UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(ROOT_URL, url)));
	}

	public static Optional<String> extractArticleUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		for (String candidate : candidateUrls(rawUrl)) {
			Optional<String> articleUrl = normalizeDirectArticleUrl(candidate);
			if (articleUrl.isPresent()) {
				return articleUrl;
			}
		}
		return Optional.empty();
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
		return extractArticleUrl(url).isPresent();
	}

	private static Optional<String> normalizeDirectArticleUrl(String url) {
		if (url == null || url.isBlank()) {
			return Optional.empty();
		}
		String normalizedUrl;
		try {
			normalizedUrl = UrlNormalizer.removeTrackingParams(UrlNormalizer.resolve(ROOT_URL, url));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		URI uri;
		try {
			uri = URI.create(normalizedUrl);
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		String host = uri.getHost();
		String path = uri.getPath();
		if (host == null || path == null) {
			return Optional.empty();
		}
		String normalizedHost = host.toLowerCase(Locale.ROOT);
		String normalizedPath = path.toLowerCase(Locale.ROOT);
		if ((normalizedHost.equals("1news.az") || normalizedHost.endsWith(".1news.az"))
				&& normalizedPath.startsWith("/az/news/")
				&& EXTERNAL_ID_PATTERN.matcher(path).find()) {
			return Optional.of(normalizedUrl);
		}
		return Optional.empty();
	}

	private static List<String> candidateUrls(String rawUrl) {
		List<String> candidates = new ArrayList<>();
		candidates.add(rawUrl.trim());
		try {
			URI uri = URI.create(rawUrl.trim());
			String rawQuery = uri.getRawQuery();
			if (rawQuery == null || rawQuery.isBlank()) {
				return candidates;
			}
			for (String parameter : rawQuery.split("&")) {
				int separatorIndex = parameter.indexOf('=');
				if (separatorIndex < 0) {
					continue;
				}
				String name = URLDecoder.decode(parameter.substring(0, separatorIndex), StandardCharsets.UTF_8);
				if (!isRedirectUrlParameter(name)) {
					continue;
				}
				String value = URLDecoder.decode(parameter.substring(separatorIndex + 1), StandardCharsets.UTF_8);
				if (!value.isBlank()) {
					candidates.add(value);
				}
			}
		} catch (IllegalArgumentException exception) {
			return candidates;
		}
		return candidates;
	}

	private static boolean isRedirectUrlParameter(String name) {
		String normalized = name.toLowerCase(Locale.ROOT);
		return normalized.equals("q")
				|| normalized.equals("url")
				|| normalized.equals("adurl");
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
