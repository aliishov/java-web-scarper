package org.raul.javawebscarper.scraper.adapter.threads;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsDateParserTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC);
	private final ThreadsDateParser parser = new ThreadsDateParser(clock);

	@Test
	void parsesIsoTimestamp() {
		assertThat(parser.parse("2026-07-22T10:15:30.000Z")).isPresent();
	}

	@Test
	void parsesRelativeTimestamp() {
		assertThat(parser.parse("2h")).contains(
				Instant.parse("2026-07-23T10:00:00Z").atZone(ZoneOffset.UTC).toOffsetDateTime()
		);
	}
}
