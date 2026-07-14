package org.raul.javawebscarper.scraper.adapter.qafqazinfoaz;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class QafqazInfoAzDateParserTests {

	private final QafqazInfoAzDateParser parser = new QafqazInfoAzDateParser(ZoneId.of("Asia/Baku"));

	@Test
	void parsesVisiblePipeDate() {
		assertThat(parser.parseArticleDate("26.06.2026 | 22:55", null, "https://qafqazinfo.az/news/detail/a-1"))
				.hasValueSatisfying(result -> {
					assertThat(result.date()).isEqualTo(OffsetDateTime.parse("2026-06-26T22:55:00+04:00"));
					assertThat(result.source()).isEqualTo("visible");
					assertThat(result.conflict()).isFalse();
				});
	}

	@Test
	void parsesVisibleDateVariants() {
		assertThat(parser.parseVisibleDate("26.06.2026 22:55"))
				.contains(OffsetDateTime.parse("2026-06-26T22:55:00+04:00"));
		assertThat(parser.parseVisibleDate("26.06.2026|22:55"))
				.contains(OffsetDateTime.parse("2026-06-26T22:55:00+04:00"));
		assertThat(parser.parseVisibleDate("26-06-2026 | 22:55"))
				.contains(OffsetDateTime.parse("2026-06-26T22:55:00+04:00"));
	}

	@Test
	void parsesDatetimeAttributeAsFallback() {
		assertThat(parser.parseArticleDate(null, "06.26.2026 | 22:55", "https://qafqazinfo.az/news/detail/a-1"))
				.hasValueSatisfying(result -> {
					assertThat(result.date()).isEqualTo(OffsetDateTime.parse("2026-06-26T22:55:00+04:00"));
					assertThat(result.source()).isEqualTo("datetime");
				});
	}

	@Test
	void visibleDateWinsWhenDatetimeConflicts() {
		assertThat(parser.parseArticleDate(
				"26.06.2026 | 22:55",
				"06.25.2026 | 22:55",
				"https://qafqazinfo.az/news/detail/a-1"
		)).hasValueSatisfying(result -> {
			assertThat(result.date()).isEqualTo(OffsetDateTime.parse("2026-06-26T22:55:00+04:00"));
			assertThat(result.source()).isEqualTo("visible");
			assertThat(result.conflict()).isTrue();
		});
	}

	@Test
	void returnsEmptyForInvalidDate() {
		assertThat(parser.parseArticleDate("not a date", "also invalid", "https://qafqazinfo.az/news/detail/a-1"))
				.isEmpty();
	}
}
