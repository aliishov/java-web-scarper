package org.raul.javawebscarper.scraper.adapter.facebook;

import org.raul.javawebscarper.model.Source;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FacebookScraperSupport {

	public static final String SOURCE_CODE = "FACEBOOK";
	public static final String BASE_URL = "https://www.facebook.com/";
	private static final Set<String> SUPPORTED_CODES = Set.of("FACEBOOK", "FB", "FACEBOOK_COM", "META_FACEBOOK");
	private static final Set<String> SUPPORTED_LANGUAGES = Set.of("az", "ru", "en", "tr");
	private static final List<String> UI_TEXT_LINES = List.of(
			"like", "comment", "share", "send", "follow", "following", "sponsored",
			"нравится", "комментировать", "поделиться", "отправить", "реклама",
			"bəyən", "şərh", "paylaş", "göndər", "sponsorlu"
	);

	private FacebookScraperSupport() {
	}

	public static boolean supports(Source source) {
		if (source == null) {
			return false;
		}
		String code = source.getCode();
		if (code != null && SUPPORTED_CODES.contains(code.trim().toUpperCase(Locale.ROOT))) {
			return true;
		}
		String baseUrl = source.getBaseUrl();
		if (baseUrl == null) {
			return false;
		}
		String normalized = baseUrl.toLowerCase(Locale.ROOT);
		return normalized.contains("facebook.com") || normalized.contains("m.facebook.com");
	}

	public static String normalizeLanguage(String value, String fallback) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("az")) {
			return "az";
		}
		if (normalized.startsWith("ru")) {
			return "ru";
		}
		if (normalized.startsWith("en")) {
			return "en";
		}
		if (normalized.startsWith("tr")) {
			return "tr";
		}
		String fallbackNormalized = fallback == null ? "" : fallback.trim().toLowerCase(Locale.ROOT);
		return SUPPORTED_LANGUAGES.contains(fallbackNormalized) ? fallbackNormalized : null;
	}

	public static boolean isSponsoredText(String text) {
		String normalized = normalizeText(text).toLowerCase(Locale.ROOT);
		return normalized.contains("sponsored")
				|| normalized.contains("advertisement")
				|| normalized.contains("реклама")
				|| normalized.contains("продвигается")
				|| normalized.contains("sponsorlu")
				|| normalized.contains("reklam");
	}

	public static boolean isExpandLabel(String text) {
		String normalized = normalizeText(text).toLowerCase(Locale.ROOT);
		return normalized.matches("^(see more|see more\\.\\.\\.|more|read more|show more|ещё|еще|показать ещё|показать еще|показать больше|daha çox|daha cox|devamını gör|devamini gor)$");
	}

	public static boolean isAllowedMediaUrl(String mediaUrl) {
		if (mediaUrl == null || mediaUrl.isBlank()) {
			return false;
		}
		String normalized = mediaUrl.toLowerCase(Locale.ROOT);
		return (normalized.startsWith("http://") || normalized.startsWith("https://"))
				&& !normalized.startsWith("blob:")
				&& !normalized.startsWith("data:")
				&& !normalized.contains("emoji")
				&& !normalized.contains("rsrc.php")
				&& !normalized.contains("static.xx.fbcdn.net");
	}

	public static boolean isAllowedAvatarUrl(String avatarUrl) {
		if (avatarUrl == null || avatarUrl.isBlank()) {
			return false;
		}
		String normalized = avatarUrl.toLowerCase(Locale.ROOT);
		return (normalized.startsWith("http://") || normalized.startsWith("https://"))
				&& !normalized.startsWith("blob:")
				&& !normalized.startsWith("data:")
				&& !normalized.contains("emoji")
				&& !normalized.contains("rsrc.php");
	}

	public static boolean isUiText(String text) {
		String normalized = normalizeText(text).toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return true;
		}
		return UI_TEXT_LINES.stream().anyMatch(line -> normalized.equals(line) || normalized.startsWith(line + " "));
	}

	public static String normalizeText(String text) {
		if (text == null) {
			return "";
		}
		String normalized = text
				.replace('\u00A0', ' ')
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.replaceAll(" *\\n+ *", "\n")
				.trim();
		List<String> lines = normalized.lines()
				.map(String::trim)
				.filter(line -> !line.isBlank())
				.toList();
		return String.join("\n", lines);
	}

	public static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}
}
