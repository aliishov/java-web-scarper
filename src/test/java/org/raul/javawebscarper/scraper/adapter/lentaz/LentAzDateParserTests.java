package org.raul.javawebscarper.scraper.adapter.lentaz;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class LentAzDateParserTests {

	private final LentAzDateParser parser = new LentAzDateParser(ZoneId.of("Asia/Baku"));

	@Test
	void parsesArticleDateWithUtcSuffix() {
		assertThat(parser.parseArticleDate("27 iyun 2026 11:40 (UTC +04:00)"))
				.contains(OffsetDateTime.parse("2026-06-27T11:40:00+04:00"));
	}

	@Test
	void parsesArticleDateWithoutSuffix() {
		assertThat(parser.parseArticleDate("27 iyun 2026 11:40"))
				.contains(OffsetDateTime.parse("2026-06-27T11:40:00+04:00"));
	}

	@Test
	void parsesArticleDateWithComma() {
		assertThat(parser.parseArticleDate("27 iyun 2026, 11:40"))
				.contains(OffsetDateTime.parse("2026-06-27T11:40:00+04:00"));
	}

	@Test
	void parsesTimeFirstArticleDate() {
		assertThat(parser.parseArticleDate("11:40 27 iyun 2026"))
				.contains(OffsetDateTime.parse("2026-06-27T11:40:00+04:00"));
	}

	@Test
	void parsesSearchCardDate() {
		assertThat(parser.parseSearchCardDate("27 iyun 2026", "11:40", "https://lent.az/xeber/hadise/example-40678059"))
				.contains(OffsetDateTime.parse("2026-06-27T11:40:00+04:00"));
	}

	@Test
	void parsesMonthAbbreviations() {
		assertThat(parser.parseArticleDate("1 yan 2026 00:01"))
				.contains(OffsetDateTime.parse("2026-01-01T00:01:00+04:00"));
		assertThat(parser.parseArticleDate("31 dek 2026 23:59"))
				.contains(OffsetDateTime.parse("2026-12-31T23:59:00+04:00"));
	}

	@Test
	void detectsTimeText() {
		assertThat(parser.isTimeText("11:40")).isTrue();
		assertThat(parser.isTimeText("27 iyun 2026")).isFalse();
	}
}
