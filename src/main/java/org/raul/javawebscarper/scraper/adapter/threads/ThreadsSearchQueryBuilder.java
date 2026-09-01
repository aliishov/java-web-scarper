package org.raul.javawebscarper.scraper.adapter.threads;

import org.raul.javawebscarper.model.enumerated.SearchRegion;
import org.raul.javawebscarper.scraper.support.SocialSearchRegionContext;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class ThreadsSearchQueryBuilder {

	public String buildSearchUrl(String baseUrl, String keyword) {
		return buildSearchUrl(baseUrl, keyword, SocialSearchRegionContext.DEFAULT_REGION);
	}

	public String buildSearchUrl(String baseUrl, String keyword, SearchRegion searchRegion) {
		String base = baseUrl == null || baseUrl.isBlank() ? ThreadsScraperSupport.BASE_URL : baseUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String searchQuery = SocialSearchRegionContext.apply(keyword, searchRegion);
		String encodedKeyword = URLEncoder.encode(
				searchQuery,
				StandardCharsets.UTF_8
		).replace("+", "%20");
		return base + "/search?q=" + encodedKeyword + "&serp_type=default";
	}
}
