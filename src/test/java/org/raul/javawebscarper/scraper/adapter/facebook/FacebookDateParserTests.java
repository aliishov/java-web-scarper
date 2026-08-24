package org.raul.javawebscarper.scraper.adapter.facebook;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class
FacebookDateParserTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), BAKU);
	private final FacebookDateParser parser = new FacebookDateParser(BAKU, FIXED_CLOCK);

	@Test
	void parsesMachineReadableDates() {
		assertThat(parser.parse("2026-07-17T09:30:00+04:00")).contains(OffsetDateTime.parse("2026-07-17T09:30:00+04:00"));
		assertThat(parser.parse("1784260800")).contains(OffsetDateTime.parse("2026-07-17T08:00:00+04:00"));
		assertThat(parser.parse("17.07.2026 13:45")).contains(OffsetDateTime.parse("2026-07-17T13:45:00+04:00"));
	}

	@Test
	void parsesEnglishRelativeAndAbsoluteDates() {
		assertThat(parser.parse("Just now")).contains(OffsetDateTime.parse("2026-07-17T12:00:00+04:00"));
		assertThat(parser.parse("5m")).contains(OffsetDateTime.parse("2026-07-17T11:55:00+04:00"));
		assertThat(parser.parse("2h")).contains(OffsetDateTime.parse("2026-07-17T10:00:00+04:00"));
		assertThat(parser.parse("Yesterday at 13:40")).contains(OffsetDateTime.parse("2026-07-16T13:40:00+04:00"));
		assertThat(parser.parse("Today at 13:40")).contains(OffsetDateTime.parse("2026-07-17T13:40:00+04:00"));
		assertThat(parser.parse("Wednesday, June 17, 2026 at 9:28 PM")).contains(OffsetDateTime.parse("2026-06-17T21:28:00+04:00"));
		assertThat(parser.parse("July 16 at 9:28 PM")).contains(OffsetDateTime.parse("2026-07-16T21:28:00+04:00"));
	}

	@Test
	void parsesRussianAndAzerbaijaniRelativeDates() {
		assertThat(parser.parse("7 ч")).contains(OffsetDateTime.parse("2026-07-17T05:00:00+04:00"));
		assertThat(parser.parse("2 дн.")).contains(OffsetDateTime.parse("2026-07-15T12:00:00+04:00"));
		assertThat(parser.parse("2 месяца")).contains(OffsetDateTime.parse("2026-05-17T12:00:00+04:00"));
		assertThat(parser.parse("Dünən 13:40")).contains(OffsetDateTime.parse("2026-07-16T13:40:00+04:00"));
		assertThat(parser.parse("Bu gün / 13:40")).contains(OffsetDateTime.parse("2026-07-17T13:40:00+04:00"));
		assertThat(parser.parse("5 dəq.")).contains(OffsetDateTime.parse("2026-07-17T11:55:00+04:00"));
	}

	@Test
	void parsesLocalizedAbsoluteDates() {
		assertThat(parser.parse("14 июнь в 19:02")).contains(OffsetDateTime.parse("2026-06-14T19:02:00+04:00"));
		assertThat(parser.parse("12 iyul 2026 10:15")).contains(OffsetDateTime.parse("2026-07-12T10:15:00+04:00"));
	}

	@Test
	void rejectsMediaAccessibilityTooltips() {
		assertThat(parser.parse("Image may contain: one or more people")).isEmpty();
		assertThat(parser.parse("может быть изображение: текст")).isEmpty();
		assertThat(parser.looksLikeDate("not a publication date")).isFalse();
	}
}
