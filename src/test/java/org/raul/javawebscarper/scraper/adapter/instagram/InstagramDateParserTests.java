package org.raul.javawebscarper.scraper.adapter.instagram;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class InstagramDateParserTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private final InstagramDateParser parser = new InstagramDateParser(
			BAKU,
			Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), BAKU)
	);

	@Test
	void parsesMachineReadableDates() {
		assertThat(parser.parse("2026-07-14T10:30:00.000Z"))
				.contains(OffsetDateTime.parse("2026-07-14T14:30:00+04:00"));
		assertThat(parser.parse("2026-07-14T10:30:00+04:00"))
				.contains(OffsetDateTime.parse("2026-07-14T10:30:00+04:00"));
	}

	@Test
	void parsesAbsoluteAndRelativeFallbacks() {
		assertThat(parser.parse("2026-07-14 10:30:00"))
				.contains(OffsetDateTime.parse("2026-07-14T10:30:00+04:00"));
		assertThat(parser.parse("2h"))
				.contains(OffsetDateTime.parse("2026-07-17T10:00:00+04:00"));
		assertThat(parser.parse("3 days ago"))
				.contains(OffsetDateTime.parse("2026-07-14T12:00:00+04:00"));
	}

	@Test
	void rejectsInvalidDates() {
		assertThat(parser.parse("Image may contain one person")).isEmpty();
		assertThat(parser.parse("not a date")).isEmpty();
	}
}
