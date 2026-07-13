package org.raul.javawebscarper.scraper.adapter.caliberaz;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CaliberAzDateParserTests {

	private final CaliberAzDateParser parser = new CaliberAzDateParser(ZoneId.of("Asia/Baku"));

	@Test
	void parsesTextMonthDate() {
		assertThat(parser.parseArticleDate("25 Июня 2026 17:24"))
				.contains(OffsetDateTime.parse("2026-06-25T17:24:00+04:00"));
	}

	@Test
	void parsesLowercaseTextMonthDate() {
		assertThat(parser.parseArticleDate("25 июня 2026 17:24"))
				.contains(OffsetDateTime.parse("2026-06-25T17:24:00+04:00"));
	}

	@Test
	void parsesTextMonthDateWithComma() {
		assertThat(parser.parseArticleDate("25 июня 2026, 17:24"))
				.contains(OffsetDateTime.parse("2026-06-25T17:24:00+04:00"));
	}

	@Test
	void parsesTextMonthDateWithRussianAt() {
		assertThat(parser.parseArticleDate("25 июня 2026 в 17:24"))
				.contains(OffsetDateTime.parse("2026-06-25T17:24:00+04:00"));
	}

	@Test
	void parsesDotDate() {
		assertThat(parser.parseArticleDate("25.06.2026 17:24"))
				.contains(OffsetDateTime.parse("2026-06-25T17:24:00+04:00"));
	}

	@Test
	void parsesRepresentativeMonths() {
		assertThat(parser.parseArticleDate("1 Января 2026 00:01"))
				.contains(OffsetDateTime.parse("2026-01-01T00:01:00+04:00"));
		assertThat(parser.parseArticleDate("31 Декабря 2026 23:59"))
				.contains(OffsetDateTime.parse("2026-12-31T23:59:00+04:00"));
	}

	@Test
	void returnsEmptyForInvalidDate() {
		assertThat(parser.parseArticleDate("not a date")).isEmpty();
	}
}
