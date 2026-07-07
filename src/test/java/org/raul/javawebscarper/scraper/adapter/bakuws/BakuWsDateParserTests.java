package org.raul.javawebscarper.scraper.adapter.bakuws;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BakuWsDateParserTests {

	private final ZoneId zoneId = ZoneId.of("Asia/Baku");
	private final BakuWsDateParser parser = new BakuWsDateParser(
			zoneId,
			Clock.fixed(Instant.parse("2026-07-07T08:00:00Z"), zoneId)
	);

	@Test
	void parsesResultCardDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("27 iyun 2026", "21:41");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-27T21:41:00+04:00"));
	}

	@Test
	void parsesShortMonthResultCardDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("27 iyn 2026", "21:41");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-27T21:41:00+04:00"));
	}

	@Test
	void parsesTodayResultCardDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("Bugün", "11:45");

		assertThat(result).contains(OffsetDateTime.parse("2026-07-07T11:45:00+04:00"));
	}

	@Test
	void parsesSpacedTodayResultCardDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("Bu gün", "11:45");

		assertThat(result).contains(OffsetDateTime.parse("2026-07-07T11:45:00+04:00"));
	}

	@Test
	void parsesYesterdayResultCardDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("Dünən", "22:10");

		assertThat(result).contains(OffsetDateTime.parse("2026-07-06T22:10:00+04:00"));
	}

	@Test
	void parsesArticleDate() {
		Optional<OffsetDateTime> result = parser.parseArticleDate("27", "iyn", "2026", "21:41");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-27T21:41:00+04:00"));
	}

	@Test
	void returnsEmptyForInvalidDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("not a date", "21:41");

		assertThat(result).isEmpty();
	}
}
