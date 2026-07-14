package org.raul.javawebscarper.scraper.adapter.qafqazinfoaz;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class QafqazInfoAzScraperSupport {

	public static final String SOURCE_CODE = "QAFQAZINFO_AZ";
	public static final String BASE_URL = "https://qafqazinfo.az/";

	private static final String ARTICLE_PATH_PREFIX = "/news/detail/";
	private static final Pattern NUMERIC_SUFFIX_PATTERN = Pattern.compile(".*-(\\d+)$");

	private QafqazInfoAzScraperSupport() {
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
		return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("qafqazinfo.az");
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
		if (!(normalizedHost.equals("qafqazinfo.az") || normalizedHost.endsWith(".qafqazinfo.az"))) {
			return false;
		}
		if (!normalizedPath.startsWith(ARTICLE_PATH_PREFIX) || normalizedPath.length() <= ARTICLE_PATH_PREFIX.length()) {
			return false;
		}
		String slug = normalizedPath.substring(ARTICLE_PATH_PREFIX.length());
		return NUMERIC_SUFFIX_PATTERN.matcher(slug).matches()
				&& !slug.endsWith(".jpg")
				&& !slug.endsWith(".jpeg")
				&& !slug.endsWith(".png")
				&& !slug.endsWith(".webp");
	}

	public static Optional<String> extractExternalPostId(String postUrl) {
		Optional<String> normalizedUrl = normalizePostUrl(postUrl);
		if (normalizedUrl.isEmpty()) {
			return Optional.empty();
		}
		String slug = URI.create(normalizedUrl.get()).getPath().substring(ARTICLE_PATH_PREFIX.length());
		java.util.regex.Matcher matcher = Pattern.compile("-(\\d+)$").matcher(slug);
		if (matcher.find()) {
			return Optional.of(matcher.group(1));
		}
		return slug.isBlank() ? Optional.empty() : Optional.of(slug);
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("data:")
				&& !normalized.contains("/banners/")
				&& !normalized.contains("dynamic_banners")
				&& !normalized.contains("facebook")
				&& !normalized.contains("telegram")
				&& !normalized.contains("whatsapp")
				&& !normalized.contains("banner")
				&& !normalized.contains("logo")
				&& !normalized.contains("sprite")
				&& !normalized.contains("icon")
				&& !normalized.endsWith(".svg");
	}

	private static boolean isBlockedUrl(String url) {
		String normalized = url.toLowerCase(Locale.ROOT);
		return normalized.contains("/news/category/")
				|| normalized.contains("/news/search")
				|| normalized.contains("/banners/")
				|| normalized.contains("dynamic_banners")
				|| normalized.contains("facebook.com")
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
