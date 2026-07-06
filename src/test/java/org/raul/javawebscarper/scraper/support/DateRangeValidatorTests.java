package org.raul.javawebscarper.scraper.support;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeValidatorTests {

	private final OffsetDateTime dateFrom = OffsetDateTime.parse("2026-06-30T00:00:00Z");
	private final OffsetDateTime dateTo = OffsetDateTime.parse("2026-07-01T23:59:59Z");

	@Test
	void acceptsValidDateRange() {
		DateRangeValidator.validate(dateFrom, dateTo);
	}

	@Test
	void rejectsInvalidDateRange() {
		assertThatThrownBy(() -> DateRangeValidator.validate(dateTo, dateFrom))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dateTo");
	}

	@Test
	void checksInclusiveRange() {
		assertThat(DateRangeValidator.isInsideRange(dateFrom, dateFrom, dateTo)).isTrue();
		assertThat(DateRangeValidator.isInsideRange(dateTo, dateFrom, dateTo)).isTrue();
		assertThat(DateRangeValidator.isBeforeRange(dateFrom.minusNanos(1), dateFrom)).isTrue();
		assertThat(DateRangeValidator.isAfterRange(dateTo.plusNanos(1), dateTo)).isTrue();
	}
}
