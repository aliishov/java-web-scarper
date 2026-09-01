package org.raul.javawebscarper.scraper.adapter.xcom;

import org.raul.javawebscarper.model.enumerated.SearchRegion;
import org.raul.javawebscarper.scraper.support.SocialSearchRegionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class XComSearchQueryBuilder {

	private final ZoneId zoneId;

	@Autowired
	public XComSearchQueryBuilder(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this.zoneId = ZoneId.of(zoneId);
	}

	XComSearchQueryBuilder(ZoneId zoneId) {
		this.zoneId = zoneId;
	}

	public String buildQuery(String keyword, OffsetDateTime dateFrom, OffsetDateTime dateTo) {
		return buildQuery(keyword, dateFrom, dateTo, SocialSearchRegionContext.DEFAULT_REGION);
	}

	public String buildQuery(
			String keyword,
			OffsetDateTime dateFrom,
			OffsetDateTime dateTo,
			SearchRegion searchRegion
	) {
		List<String> parts = new ArrayList<>();
		String searchQuery = SocialSearchRegionContext.apply(keyword, searchRegion);
		if (!searchQuery.isBlank()) {
			parts.add(searchQuery);
		}
		if (dateFrom != null) {
			parts.add("since:" + dateFrom.atZoneSameInstant(zoneId).toLocalDate());
		}
		if (dateTo != null) {
			LocalDate until = dateTo.atZoneSameInstant(zoneId).toLocalDate().plusDays(1);
			parts.add("until:" + until);
		}
		return String.join(" ", parts).trim();
	}

	public String buildSearchUrl(String baseUrl, String keyword, OffsetDateTime dateFrom, OffsetDateTime dateTo, XSearchMode mode) {
		return buildSearchUrl(baseUrl, keyword, dateFrom, dateTo, mode, SocialSearchRegionContext.DEFAULT_REGION);
	}

	public String buildSearchUrl(
			String baseUrl,
			String keyword,
			OffsetDateTime dateFrom,
			OffsetDateTime dateTo,
			XSearchMode mode,
			SearchRegion searchRegion
	) {
		String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
		String query = buildQuery(keyword, dateFrom, dateTo, searchRegion);
		XSearchMode safeMode = mode == null ? XSearchMode.LATEST : mode;
		return normalizedBaseUrl + "/search?f=" + safeMode.queryValue()
				+ "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
				+ "&src=typed_query";
	}

	private String normalizeBaseUrl(String baseUrl) {
		String normalized = baseUrl == null || baseUrl.isBlank() ? XComScraperSupport.BASE_URL : baseUrl.trim();
		return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
	}
}
