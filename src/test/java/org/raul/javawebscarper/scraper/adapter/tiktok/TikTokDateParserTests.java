package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TikTokDateParserTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private final TikTokDateParser parser = new TikTokDateParser(
			BAKU,
			Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), BAKU)
	);

	@Test
	void parsesMachineReadableAndIsoDates() {
		assertThat(parser.parse("2026-07-15T08:00:00Z"))
				.contains(OffsetDateTime.parse("2026-07-15T12:00:00+04:00"));
		assertThat(parser.parse("1784112000000"))
				.contains(OffsetDateTime.parse("2026-07-15T14:40:00+04:00"));
	}

	@Test
	void parsesVisibleAbsoluteDates() {
		assertThat(parser.parse("2026-07-15"))
				.contains(OffsetDateTime.parse("2026-07-15T00:00:00+04:00"));
		assertThat(parser.parse("15.07.2026"))
				.contains(OffsetDateTime.parse("2026-07-15T00:00:00+04:00"));
		assertThat(parser.parse("7-15"))
				.contains(OffsetDateTime.parse("2026-07-15T00:00:00+04:00"));
	}

	@Test
	void parsesRelativeDatesWithFixedClock() {
		assertThat(parser.parse("2h ago"))
				.contains(OffsetDateTime.parse("2026-07-17T10:00:00+04:00"));
		assertThat(parser.parse("3d ago"))
				.contains(OffsetDateTime.parse("2026-07-14T12:00:00+04:00"));
	}

	@Test
	void rejectsInvalidDatesWithoutInventingCurrentTime() {
		assertThat(parser.parse("unknown date")).isEmpty();
	}
}
