package org.raul.javawebscarper.scraper.support;

import org.raul.javawebscarper.model.enumerated.SearchRegion;

public final class SocialSearchRegionContext {

	public static final SearchRegion DEFAULT_REGION = SearchRegion.defaultRegion();

	private SocialSearchRegionContext() {
	}

	public static String apply(String keyword, SearchRegion region) {
		String trimmedKeyword = keyword == null ? "" : keyword.trim();
		SearchRegion safeRegion = region == null ? DEFAULT_REGION : region;
		if (trimmedKeyword.isBlank() || safeRegion == SearchRegion.GLOBAL) {
			return trimmedKeyword;
		}
		String trimmedContext = safeRegion.searchContext();
		String normalizedKeyword = KeywordTextMatcher.normalize(trimmedKeyword);
		String normalizedContext = KeywordTextMatcher.normalize(trimmedContext);
		if (normalizedContext.isBlank() || normalizedKeyword.contains(normalizedContext)) {
			return trimmedKeyword;
		}
		return trimmedKeyword + " " + trimmedContext;
	}

	public static boolean matches(String text, String language, SearchRegion region) {
		SearchRegion safeRegion = region == null ? DEFAULT_REGION : region;
		if (safeRegion == SearchRegion.GLOBAL) {
			return true;
		}
		if (safeRegion.supportsLanguageHint(language)) {
			return true;
		}
		String normalizedText = KeywordTextMatcher.normalize(text);
		for (String alias : safeRegion.aliases()) {
			String normalizedAlias = KeywordTextMatcher.normalize(alias);
			if (!normalizedAlias.isBlank() && normalizedText.contains(normalizedAlias)) {
				return true;
			}
		}
		return false;
	}
}
