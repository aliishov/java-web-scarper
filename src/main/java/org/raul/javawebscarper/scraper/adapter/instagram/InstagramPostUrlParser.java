package org.raul.javawebscarper.scraper.adapter.instagram;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class InstagramPostUrlParser {

	private static final String CANONICAL_BASE_URL = "https://www.instagram.com";
	private static final URI BASE_URI = URI.create(CANONICAL_BASE_URL + "/");
	private static final Pattern SHORTCODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{2,128}");

	private InstagramPostUrlParser() {
	}

	public static Optional<InstagramPostUrl> parse(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = toUri(rawUrl.trim());
			if (!isInstagramHost(uri.getHost())) {
				return Optional.empty();
			}
			String[] segments = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
			if (segments.length < 3) {
				return Optional.empty();
			}
			InstagramPostType type = typeFromPathSegment(segments[1]).orElse(null);
			String shortcode = segments[2];
			if (type == null || !SHORTCODE_PATTERN.matcher(shortcode).matches()) {
				return Optional.empty();
			}
			return Optional.of(new InstagramPostUrl(shortcode, canonicalUrl(type, shortcode), type));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	public static String canonicalUrl(InstagramPostType type, String shortcode) {
		return CANONICAL_BASE_URL + "/" + type.pathSegment() + "/" + shortcode + "/";
	}

	private static URI toUri(String rawUrl) {
		String value = rawUrl;
		if (value.startsWith("//")) {
			value = "https:" + value;
		} else if (value.startsWith("instagram.com") || value.startsWith("www.instagram.com")) {
			value = "https://" + value;
		}
		return BASE_URI.resolve(URI.create(value));
	}

	private static Optional<InstagramPostType> typeFromPathSegment(String segment) {
		if (segment == null) {
			return Optional.empty();
		}
		return switch (segment.toLowerCase(Locale.ROOT)) {
			case "p" -> Optional.of(InstagramPostType.POST);
			case "reel", "reels" -> Optional.of(InstagramPostType.REEL);
			case "tv" -> Optional.of(InstagramPostType.TV);
			default -> Optional.empty();
		};
	}

	private static boolean isInstagramHost(String host) {
		if (host == null) {
			return false;
		}
		String normalized = host.toLowerCase(Locale.ROOT);
		return normalized.equals("instagram.com") || normalized.equals("www.instagram.com");
	}
}
