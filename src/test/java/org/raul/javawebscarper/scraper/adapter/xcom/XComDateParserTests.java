package org.raul.javawebscarper.scraper.adapter.xcom;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class XComDateParserTests {

	private final XComDateParser parser = new XComDateParser();

	@Test
	void parsesTweetDatetimeAttribute() {
		assertThat(parser.parseDatetime("2026-07-14T08:30:00.000Z"))
				.contains(OffsetDateTime.parse("2026-07-14T08:30:00Z"));
	}

	@Test
	void returnsEmptyForInvalidDatetime() {
		assertThat(parser.parseDatetime("Bu gun / 12:02")).isEmpty();
	}
}
