package org.raul.javawebscarper.scraper.adapter.facebook;

import org.raul.javawebscarper.scraper.support.TextHashGenerator;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FacebookPostUrlParser {

	private static final String BASE_URL = "https://www.facebook.com/";
	private static final String CANONICAL_HOST = "www.facebook.com";
	private static final Set<String> TRACKING_PARAMS = Set.of(
			"__cft__",
			"__tn__",
			"mibextid",
			"ref",
			"refid",
			"locale",
			"fbclid"
	);

	private FacebookPostUrlParser() {
	}

	public static Optional<FacebookPostUrl> parse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		URI uri;
		try {
			uri = URI.create(UrlNormalizer.resolve(BASE_URL, value.trim()));
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
		if (!isFacebookHost(uri.getHost())) {
			return Optional.empty();
		}
		String path = normalizePath(uri.getPath());
		if (path.isBlank() || isNavigationPath(path)) {
			return Optional.empty();
		}
		Map<String, String> query = queryParams(uri.getRawQuery());
		return parsePath(path, query);
	}

	public static String fallbackExternalPostId(String canonicalUrl) {
		return TextHashGenerator.sha256(canonicalUrl == null ? "" : canonicalUrl).substring(0, 32);
	}

	private static Optional<FacebookPostUrl> parsePath(String path, Map<String, String> query) {
		String[] segments = path.substring(1).split("/");
		if (segments.length >= 4 && "groups".equalsIgnoreCase(segments[0]) && "posts".equalsIgnoreCase(segments[2])) {
			String groupId = segments[1];
			String postId = segments[3];
			return canonical(
					postId,
					"https://" + CANONICAL_HOST + "/groups/" + groupId + "/posts/" + postId,
					groupId,
					FacebookPostType.GROUP_POST
			);
		}
		if (segments.length >= 3 && "posts".equalsIgnoreCase(segments[1])) {
			String author = segments[0];
			String postId = segments[2];
			return canonical(
					postId,
					"https://" + CANONICAL_HOST + "/" + author + "/posts/" + postId,
					author,
					FacebookPostType.POST
			);
		}
		if (segments.length >= 3 && "share".equalsIgnoreCase(segments[0]) && "p".equalsIgnoreCase(segments[1])) {
			String shareId = segments[2];
			return canonical(
					shareId,
					"https://" + CANONICAL_HOST + "/share/p/" + shareId,
					null,
					FacebookPostType.POST
			);
		}
		if (segments.length >= 2 && "reel".equalsIgnoreCase(segments[0])) {
			String reelId = segments[1];
			return canonical(
					reelId,
					"https://" + CANONICAL_HOST + "/reel/" + reelId,
					null,
					FacebookPostType.REEL
			);
		}
		if (segments.length >= 1 && "watch".equalsIgnoreCase(segments[0])) {
			String videoId = query.get("v");
			return canonical(
					videoId,
					"https://" + CANONICAL_HOST + "/watch?v=" + videoId,
					null,
					FacebookPostType.WATCH
			);
		}
		if (segments.length >= 1 && "photo".equalsIgnoreCase(segments[0])) {
			String photoId = query.get("fbid");
			String author = query.get("id");
			String canonical = "https://" + CANONICAL_HOST + "/photo?fbid=" + photoId
					+ (isPresent(author) ? "&id=" + author : "");
			return canonical(photoId, canonical, author, FacebookPostType.PHOTO);
		}
		if (segments.length >= 1 && ("story.php".equalsIgnoreCase(segments[0]) || "permalink.php".equalsIgnoreCase(segments[0]))) {
			String storyId = query.get("story_fbid");
			String author = query.get("id");
			String canonical = "https://" + CANONICAL_HOST + "/" + segments[0] + "?story_fbid=" + storyId
					+ (isPresent(author) ? "&id=" + author : "");
			return canonical(storyId, canonical, author, FacebookPostType.STORY);
		}
		return Optional.empty();
	}

	private static Optional<FacebookPostUrl> canonical(
			String externalPostId,
			String canonicalUrl,
			String authorExternalId,
			FacebookPostType type
	) {
		if (!isPresent(externalPostId) || canonicalUrl.contains("null")) {
			return Optional.empty();
		}
		return Optional.of(new FacebookPostUrl(externalPostId, canonicalUrl, authorExternalId, type));
	}

	private static boolean isFacebookHost(String host) {
		if (host == null || host.isBlank()) {
			return false;
		}
		String normalized = host.toLowerCase(Locale.ROOT);
		return normalized.equals("facebook.com")
				|| normalized.equals("www.facebook.com")
				|| normalized.equals("m.facebook.com")
				|| normalized.endsWith(".facebook.com");
	}

	private static String normalizePath(String path) {
		if (path == null || path.isBlank()) {
			return "";
		}
		String normalized = path.replaceAll("/{2,}", "/");
		return normalized.endsWith("/") && normalized.length() > 1
				? normalized.substring(0, normalized.length() - 1)
				: normalized;
	}

	private static boolean isNavigationPath(String path) {
		String normalized = path.toLowerCase(Locale.ROOT);
		return normalized.startsWith("/search/")
				|| normalized.equals("/search")
				|| normalized.startsWith("/marketplace")
				|| normalized.startsWith("/events")
				|| normalized.startsWith("/friends")
				|| normalized.startsWith("/groups/feed")
				|| normalized.startsWith("/watch/live")
				|| normalized.startsWith("/login");
	}

	private static Map<String, String> queryParams(String rawQuery) {
		Map<String, String> params = new LinkedHashMap<>();
		if (rawQuery == null || rawQuery.isBlank()) {
			return params;
		}
		for (String pair : rawQuery.split("&")) {
			int separator = pair.indexOf('=');
			String rawName = separator < 0 ? pair : pair.substring(0, separator);
			String name = decode(rawName);
			if (name.isBlank() || TRACKING_PARAMS.contains(name.toLowerCase(Locale.ROOT))) {
				continue;
			}
			String value = separator < 0 ? "" : decode(pair.substring(separator + 1));
			params.putIfAbsent(name, value);
		}
		return params;
	}

	private static String decode(String value) {
		return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}
