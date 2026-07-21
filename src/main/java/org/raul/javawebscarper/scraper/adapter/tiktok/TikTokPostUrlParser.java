package org.raul.javawebscarper.scraper.adapter.tiktok;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TikTokPostUrlParser {

	private static final String CANONICAL_BASE_URL = "https://www.tiktok.com";
	private static final URI BASE_URI = URI.create(CANONICAL_BASE_URL + "/");
	private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("\\d{6,32}");
	private static final Pattern SHORT_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,128}");

	private TikTokPostUrlParser() {
	}

	public static Optional<TikTokPostUrl> parse(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = toUri(rawUrl.trim());
			if (!isTikTokHost(uri.getHost())) {
				return Optional.empty();
			}
			String[] segments = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
			if (segments.length >= 4 && segments[1].startsWith("@") && "video".equalsIgnoreCase(segments[2])) {
				String username = cleanUsername(segments[1].substring(1));
				String videoId = segments[3];
				if (username.isBlank() || !VIDEO_ID_PATTERN.matcher(videoId).matches()) {
					return Optional.empty();
				}
				return Optional.of(new TikTokPostUrl(
						videoId,
						canonicalVideoUrl(username, videoId),
						username,
						false
				));
			}
			if (segments.length >= 3 && "t".equalsIgnoreCase(segments[1]) && SHORT_CODE_PATTERN.matcher(segments[2]).matches()) {
				return Optional.of(new TikTokPostUrl(null, CANONICAL_BASE_URL + "/t/" + segments[2], null, true));
			}
			return Optional.empty();
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	public static String canonicalVideoUrl(String username, String videoId) {
		return CANONICAL_BASE_URL + "/@" + cleanUsername(username) + "/video/" + videoId;
	}

	public static Optional<String> externalPostId(String rawUrl) {
		return parse(rawUrl)
				.filter(postUrl -> !postUrl.shortUrl())
				.map(TikTokPostUrl::externalPostId);
	}

	private static URI toUri(String rawUrl) {
		String value = rawUrl;
		if (value.startsWith("//")) {
			value = "https:" + value;
		} else if (value.startsWith("tiktok.com") || value.startsWith("www.tiktok.com")) {
			value = "https://" + value;
		}
		return BASE_URI.resolve(URI.create(value));
	}

	private static String cleanUsername(String value) {
		String decoded = URLDecoder.decode(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
		return decoded.replace("@", "");
	}

	private static boolean isTikTokHost(String host) {
		if (host == null) {
			return false;
		}
		String normalized = host.toLowerCase(Locale.ROOT);
		return normalized.equals("tiktok.com") || normalized.equals("www.tiktok.com");
	}
}
