package org.raul.javawebscarper.scraper.adapter.instagram;

import org.raul.javawebscarper.model.enumerated.SearchRegion;
import org.raul.javawebscarper.scraper.support.SocialSearchRegionContext;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class InstagramSearchQueryBuilder {

	public String buildSearchUrl(String baseUrl, String keyword, InstagramSearchMode searchMode) {
		return buildSearchUrl(baseUrl, keyword, searchMode, SocialSearchRegionContext.DEFAULT_REGION);
	}

	public String buildSearchUrl(
			String baseUrl,
			String keyword,
			InstagramSearchMode searchMode,
			SearchRegion searchRegion
	) {
		InstagramSearchMode mode = resolveMode(keyword, searchMode);
		String base = normalizeBaseUrl(baseUrl);
		if (mode == InstagramSearchMode.HASHTAG) {
			String hashtag = normalizeHashtag(keyword);
			if (hashtag.isBlank()) {
				return base + "/explore/search/keyword/?q=" + encode(keyword);
			}
			return base + "/explore/tags/" + encodePathSegment(hashtag) + "/";
		}
		String searchQuery = SocialSearchRegionContext.apply(keyword, searchRegion);
		return base + "/explore/search/keyword/?q=" + encode(searchQuery);
	}

	public InstagramSearchMode resolveMode(String keyword, InstagramSearchMode configuredMode) {
		InstagramSearchMode safeMode = configuredMode == null ? InstagramSearchMode.AUTO : configuredMode;
		if (safeMode == InstagramSearchMode.AUTO) {
			return keyword != null && keyword.trim().startsWith("#") ? InstagramSearchMode.HASHTAG : InstagramSearchMode.KEYWORD;
		}
		return safeMode;
	}

	public boolean hashtagFallbackAllowed(String keyword) {
		String hashtag = normalizeHashtag(keyword);
		return !hashtag.isBlank() && hashtag.matches("[\\p{L}\\p{N}_]{1,100}");
	}

	private String normalizeHashtag(String keyword) {
		return keyword == null ? "" : keyword.trim().replaceFirst("^#+", "").trim();
	}

	private String normalizeBaseUrl(String baseUrl) {
		String base = baseUrl == null || baseUrl.isBlank() ? InstagramScraperSupport.BASE_URL : baseUrl.trim();
		return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	private String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
	}

	private String encodePathSegment(String value) {
		return encode(value).replace("%2F", "");
	}
}
