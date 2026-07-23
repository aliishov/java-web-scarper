package org.raul.javawebscarper.scraper.adapter.threads;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class ThreadsSearchQueryBuilder {

	public String buildSearchUrl(String baseUrl, String keyword) {
		String base = baseUrl == null || baseUrl.isBlank() ? ThreadsScraperSupport.BASE_URL : baseUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String encodedKeyword = URLEncoder.encode(
				keyword == null ? "" : keyword.trim(),
				StandardCharsets.UTF_8
		).replace("+", "%20");
		return base + "/search?q=" + encodedKeyword + "&serp_type=default";
	}
}
