package org.raul.javawebscarper.scraper.adapter.instagram;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class InstagramSearchQueryBuilder {

	public String buildSearchUrl(String baseUrl, String keyword, InstagramSearchMode searchMode) {
		InstagramSearchMode mode = resolveMode(keyword, searchMode);
		String base = normalizeBaseUrl(baseUrl);
		if (mode == InstagramSearchMode.HASHTAG) {
			String hashtag = normalizeHashtag(keyword);
			if (hashtag.isBlank()) {
				return base + "/explore/search/keyword/?q=" + encode(keyword);
			}
			return base + "/explore/tags/" + encodePathSegment(hashtag) + "/";
		}
		return base + "/explore/search/keyword/?q=" + encode(keyword);
	}

	public InstagramSearchMode resolveMode(String keyword, InstagramSearchMode configuredMode) {
		InstagramSearchMode safeMode = configuredMode == null ? InstagramSearchMode.AUTO : configuredMode;
		if (safeMode == InstagramSearchMode.AUTO) {
			return keyword != null && keyword.trim().startsWith("#") ? InstagramSearchMode.HASHTAG : InstagramSearchMode.KEYWORD;
		}
		return safeMode;
	}

	public boolean hashtagFallbackAllowed(String keyword) {
		String value = keyword == null ? "" : keyword.trim();
		return value.startsWith("#") && !normalizeHashtag(value).isBlank();
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
