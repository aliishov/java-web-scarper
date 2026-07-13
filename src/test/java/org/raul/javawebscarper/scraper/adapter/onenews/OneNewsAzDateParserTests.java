package org.raul.javawebscarper.scraper.adapter.onenews;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OneNewsAzDateParserTests {

	private final OneNewsAzDateParser parser = new OneNewsAzDateParser(ZoneId.of("Asia/Baku"));

	@Test
	void parsesDateWithSpacesAroundSeparators() {
		Optional<OffsetDateTime> result = parser.parseArticleDate("19:12 - 17 / 06 / 2026");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-17T19:12:00+04:00"));
	}

	@Test
	void parsesDateWithoutSlashSpaces() {
		Optional<OffsetDateTime> result = parser.parseArticleDate("19:12 - 17/06/2026");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-17T19:12:00+04:00"));
	}

	@Test
	void parsesDateWithoutDashSpaces() {
		Optional<OffsetDateTime> result = parser.parseArticleDate("19:12-17/06/2026");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-17T19:12:00+04:00"));
	}

	@Test
	void parsesSingleDigitDayAndMonth() {
		Optional<OffsetDateTime> result = parser.parseArticleDate("09:05 - 7 / 6 / 2026");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-07T09:05:00+04:00"));
	}

	@Test
	void returnsEmptyForInvalidDate() {
		Optional<OffsetDateTime> result = parser.parseArticleDate("not a 1news date", "https://1news.az/az/news/example");

		assertThat(result).isEmpty();
	}
}
