package org.raul.javawebscarper.scraper.adapter.xcom;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class XComSearchQueryBuilderTests {

	private final XComSearchQueryBuilder builder = new XComSearchQueryBuilder(ZoneId.of("Asia/Baku"));

	@Test
	void buildsLatestSearchUrlWithDateRangeOperators() {
		String url = builder.buildSearchUrl(
				"https://x.com/",
				"M\u0259hk\u0259m\u0259",
				OffsetDateTime.parse("2026-07-14T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-14T23:59:59+04:00"),
				XSearchMode.LATEST
		);

		assertThat(url).startsWith("https://x.com/search?f=live&q=");
		assertThat(url).contains("M%C9%99hk%C9%99m%C9%99");
		assertThat(url).contains("Az%C9%99rbaycan");
		assertThat(url).contains("since%3A2026-07-14");
		assertThat(url).contains("until%3A2026-07-15");
		assertThat(url).endsWith("&src=typed_query");
	}

	@Test
	void usesTopSearchModeWhenRequested() {
		String url = builder.buildSearchUrl(
				"https://x.com",
				"Ilham Aliyev",
				null,
				null,
				XSearchMode.TOP
		);

		assertThat(url).isEqualTo("https://x.com/search?f=top&q=Ilham+Aliyev+Az%C9%99rbaycan&src=typed_query");
	}

	@Test
	void buildQueryOmitsDateOperatorsWhenRangeIsMissing() {
		String query = builder.buildQuery("court", null, null);

		assertThat(query).isEqualTo("court Azərbaycan");
	}
}
