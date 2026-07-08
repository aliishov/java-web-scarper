package org.raul.javawebscarper.scheduler;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ScrapeDateRange(
		OffsetDateTime dateFrom,
		OffsetDateTime dateTo
) {

	public LocalDate fromLocalDate() {
		return dateFrom.toLocalDate();
	}

	public LocalDate toLocalDate() {
		return dateTo.toLocalDate();
	}
}
