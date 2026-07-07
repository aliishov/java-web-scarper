package org.raul.javawebscarper.scraper.adapter.bakuws;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BakuWsDateParserTests {

	private final BakuWsDateParser parser = new BakuWsDateParser(ZoneId.of("Asia/Baku"));

	@Test
	void parsesResultCardDate() {
		Optional<OffsetDateTime> result = parser.parseResultCardDate("27 iyun 2026", "21:41");

		assertThat(result).contains(OffsetDateTime.parse("2026-06-27T21:41:00+04:00"));
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
