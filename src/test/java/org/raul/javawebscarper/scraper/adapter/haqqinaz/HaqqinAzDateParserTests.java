package org.raul.javawebscarper.scraper.adapter.haqqinaz;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class HaqqinAzDateParserTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private final HaqqinAzDateParser parser = new HaqqinAzDateParser(
			BAKU,
			Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), BAKU)
	);

	@Test
	void parsesTimeOnlyAsTodayInBaku() {
		assertThat(parser.parseArticleDate("15:31"))
				.contains(OffsetDateTime.parse("2026-07-13T15:31:00+04:00"));
	}

	@Test
	void parsesTodayWithTime() {
		assertThat(parser.parseArticleDate("Сегодня, 15:31"))
				.contains(OffsetDateTime.parse("2026-07-13T15:31:00+04:00"));
	}

	@Test
	void parsesYesterdayWithTime() {
		assertThat(parser.parseArticleDate("Вчера, 15:31"))
				.contains(OffsetDateTime.parse("2026-07-12T15:31:00+04:00"));
	}

	@Test
	void parsesDotDateTime() {
		assertThat(parser.parseArticleDate("25.06.2026 15:31"))
				.contains(OffsetDateTime.parse("2026-06-25T15:31:00+04:00"));
	}

	@Test
	void parsesTimeDashDotDate() {
		assertThat(parser.parseArticleDate("15:31 - 25.06.2026"))
				.contains(OffsetDateTime.parse("2026-06-25T15:31:00+04:00"));
	}

	@Test
	void parsesTextMonthDateTime() {
		assertThat(parser.parseArticleDate("25 июня 2026, 15:31"))
				.contains(OffsetDateTime.parse("2026-06-25T15:31:00+04:00"));
	}

	@Test
	void parsesTimeTextMonthDate() {
		assertThat(parser.parseArticleDate("15:31, 25 июня 2026"))
				.contains(OffsetDateTime.parse("2026-06-25T15:31:00+04:00"));
	}

	@Test
	void returnsEmptyForInvalidDate() {
		assertThat(parser.parseArticleDate("not a date")).isEmpty();
	}
}
