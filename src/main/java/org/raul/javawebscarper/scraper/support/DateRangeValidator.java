package org.raul.javawebscarper.scraper.support;

import java.time.OffsetDateTime;

public final class DateRangeValidator {

	private DateRangeValidator() {
	}

	public static void validate(OffsetDateTime dateFrom, OffsetDateTime dateTo) {
		if (dateFrom == null) {
			throw new IllegalArgumentException("dateFrom must not be null");
		}
		if (dateTo == null) {
			throw new IllegalArgumentException("dateTo must not be null");
		}
		if (dateTo.isBefore(dateFrom)) {
			throw new IllegalArgumentException("dateTo must be greater than or equal to dateFrom");
		}
	}

	public static boolean isInsideRange(
			OffsetDateTime postDate,
			OffsetDateTime dateFrom,
			OffsetDateTime dateTo
	) {
		if (postDate == null) {
			return false;
		}
		return !isBeforeRange(postDate, dateFrom) && !isAfterRange(postDate, dateTo);
	}

	public static boolean isBeforeRange(OffsetDateTime postDate, OffsetDateTime dateFrom) {
		return postDate != null && dateFrom != null && postDate.isBefore(dateFrom);
	}

	public static boolean isAfterRange(OffsetDateTime postDate, OffsetDateTime dateTo) {
		return postDate != null && dateTo != null && postDate.isAfter(dateTo);
	}
}
