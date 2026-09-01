package org.raul.javawebscarper.scraper.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

public final class KeywordTextMatcher {

	private KeywordTextMatcher() {
	}

	public static Optional<String> findMatch(String text, String keyword) {
		String normalizedKeyword = normalize(keyword);
		if (normalizedKeyword.isBlank()) {
			return Optional.empty();
		}
		String normalizedText = normalize(text);
		if (normalizedText.isBlank()) {
			return Optional.empty();
		}
		String searchableText = " " + normalizedText + " ";
		String searchableKeyword = " " + normalizedKeyword + " ";
		return searchableText.contains(searchableKeyword) ? Optional.of(keyword.trim()) : Optional.empty();
	}

	static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String lower = value.toLowerCase(Locale.ROOT);
		StringBuilder transliterated = new StringBuilder(lower.length());
		for (int index = 0; index < lower.length(); index++) {
			transliterated.append(transliterate(lower.charAt(index)));
		}
		String withoutDiacritics = Normalizer.normalize(transliterated, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "");
		return withoutDiacritics.replaceAll("[^\\p{Alnum}]+", " ").trim().replaceAll("\\s+", " ");
	}

	private static char transliterate(char value) {
		return switch (value) {
			case 'ə' -> 'e';
			case 'ı' -> 'i';
			case 'ö' -> 'o';
			case 'ü' -> 'u';
			case 'ğ' -> 'g';
			case 'ç' -> 'c';
			case 'ş' -> 's';
			default -> value;
		};
	}
}
