package org.raul.javawebscarper.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PreviousDayDateRangeResolverTests {

	private final PreviousDayDateRangeResolver resolver = new PreviousDayDateRangeResolver();

	@Test
	void resolvesPreviousCalendarDayInConfiguredZone() {
		ScrapeDateRange range = resolver.resolve(
				LocalDate.of(2026, 7, 8),
				ZoneId.of("Asia/Baku")
		);

		assertThat(range.dateFrom()).isEqualTo(OffsetDateTime.parse("2026-07-07T00:00+04:00"));
		assertThat(range.dateTo()).isEqualTo(OffsetDateTime.parse("2026-07-07T23:59:59.999999999+04:00"));
		assertThat(range.fromLocalDate()).isEqualTo(LocalDate.of(2026, 7, 7));
		assertThat(range.toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 7));
	}
}
