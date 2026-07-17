package org.raul.javawebscarper.scraper.adapter.facebook;

import org.raul.javawebscarper.scraper.support.UrlNormalizer;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class FacebookSearchQueryBuilder {

	public String buildSearchUrl(String baseUrl, String keyword, FacebookSearchMode searchMode) {
		String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
		String encodedKeyword = URLEncoder.encode(keyword == null ? "" : keyword.trim(), StandardCharsets.UTF_8);
		String url = normalizedBaseUrl + "search/posts/?q=" + encodedKeyword;
		if (searchMode == FacebookSearchMode.RECENT) {
			return url + "&filters=eyJycF9jcmVhdGlvbl90aW1lOjAiOiJ7XCJuYW1lXCI6XCJyZWNlbnRfcG9zdHNcIixcImFyZ3NcIjpcIlwifSJ9";
		}
		return url;
	}

	private String normalizeBaseUrl(String baseUrl) {
		String value = baseUrl == null || baseUrl.isBlank() ? FacebookScraperSupport.BASE_URL : baseUrl.trim();
		String normalized = UrlNormalizer.normalize(value);
		return normalized.endsWith("/") ? normalized : normalized + "/";
	}
}
