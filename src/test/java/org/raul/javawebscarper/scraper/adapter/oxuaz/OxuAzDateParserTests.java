package org.raul.javawebscarper.scraper.adapter.oxuaz;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OxuAzDateParserTests {

	private final ZoneId zoneId = ZoneId.of("Asia/Baku");
	private final OxuAzDateParser parser = new OxuAzDateParser(
			zoneId,
			Clock.fixed(Instant.parse("2026-07-08T08:00:00Z"), zoneId)
	);

	@Test
	void parsesTodayResultCardDateWithSpacedWord() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("Bu g\u00fcn / 12:02");

		assertThat(result).contains(OffsetDateTime.parse("2026-07-08T12:02:00+04:00"));
	}

	@Test
	void parsesTodayResultCardDateWithSingleWord() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("Bug\u00fcn / 12:02");

		assertThat(result).contains(OffsetDateTime.parse("2026-07-08T12:02:00+04:00"));
	}

	@Test
	void parsesYesterdayResultCardDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("D\u00fcn\u0259n / 22:10");

		assertThat(result).contains(OffsetDateTime.parse("2026-07-07T22:10:00+04:00"));
	}

	@Test
	void parsesAbsoluteDateWithSlashSeparator() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("25.06.2026 / 12:40");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
	}

	@Test
	void parsesAbsoluteDateWithoutSlashSeparator() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("25.06.2026 12:40");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
	}

	@Test
	void returnsEmptyForInvalidDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("not a date");

		assertThat(result).isEmpty();
	}
}
