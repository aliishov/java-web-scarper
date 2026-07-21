package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class TikTokSearchQueryBuilder {

	public String buildSearchUrl(String baseUrl, String keyword, TikTokSearchMode searchMode) {
		TikTokSearchMode mode = resolveMode(keyword, searchMode);
		String base = normalizeBaseUrl(baseUrl);
		if (mode == TikTokSearchMode.HASHTAG) {
			String hashtag = normalizeHashtag(keyword);
			if (!hashtag.isBlank()) {
				return base + "/tag/" + encodePathSegment(hashtag);
			}
		}
		return buildKeywordSearchUrl(baseUrl, keyword);
	}

	public String buildKeywordSearchUrl(String baseUrl, String keyword) {
		return normalizeBaseUrl(baseUrl) + "/search/video?q=" + encode(keyword);
	}

	public TikTokSearchMode resolveMode(String keyword, TikTokSearchMode configuredMode) {
		TikTokSearchMode safeMode = configuredMode == null ? TikTokSearchMode.AUTO : configuredMode;
		if (safeMode == TikTokSearchMode.AUTO) {
			return keyword != null && keyword.trim().startsWith("#") ? TikTokSearchMode.HASHTAG : TikTokSearchMode.KEYWORD;
		}
		return safeMode;
	}

	private String normalizeHashtag(String keyword) {
		return keyword == null ? "" : keyword.trim().replaceFirst("^#+", "").trim();
	}

	private String normalizeBaseUrl(String baseUrl) {
		String base = baseUrl == null || baseUrl.isBlank() ? TikTokScraperSupport.BASE_URL : baseUrl.trim();
		return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	private String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
	}

	private String encodePathSegment(String value) {
		return encode(value).replace("%2F", "");
	}
}
