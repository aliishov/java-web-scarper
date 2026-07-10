package org.raul.javawebscarper.scraper.adapter.mediaaz;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MediaAzDateParserTests {

	private final MediaAzDateParser parser = new MediaAzDateParser(ZoneId.of("Asia/Baku"));

	@Test
	void parsesResultCardTimestamp() {
		Optional<OffsetDateTime> result = parser.parseResultCardTimestamp("2026-06-25 12:40:00");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
	}

	@Test
	void parsesVisibleDate() {
		Optional<OffsetDateTime> result = parser.parseVisibleDate("25.06.2026 12:40");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
	}

	@Test
	void parsesArticleDate() {
		Optional<OffsetDateTime> result = parser.parseArticleDate("25.06.2026 12:40");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-25T12:40:00+04:00"));
	}

	@Test
	void returnsEmptyForInvalidDate() {
		Optional<OffsetDateTime> result = parser.parseVisibleDate("not a date");

		assertThat(result).isEmpty();
	}
}
