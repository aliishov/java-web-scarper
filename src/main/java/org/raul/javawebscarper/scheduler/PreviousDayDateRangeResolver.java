package org.raul.javawebscarper.scheduler;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class PreviousDayDateRangeResolver {

	public ScrapeDateRange resolve(ZoneId zoneId) {
		return resolve(LocalDate.now(zoneId), zoneId);
	}

	public ScrapeDateRange resolve(LocalDate today, ZoneId zoneId) {
		LocalDate previousDay = today.minusDays(1);
		return new ScrapeDateRange(
				previousDay.atStartOfDay(zoneId).toOffsetDateTime(),
				previousDay.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toOffsetDateTime()
		);
	}
}
