package org.raul.javawebscarper.scraper.adapter.lentaz;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
public class LentAzSearchPeriodResolver {

	static final int LAST_WEEK = 1;
	static final int LAST_MONTH = 2;
	static final int LAST_SIX_MONTHS = 3;
	static final int ALL_TIME = 4;

	private final Clock clock;
	private final ZoneId zoneId;

	@Autowired
	public LentAzSearchPeriodResolver(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(Clock.system(ZoneId.of(zoneId)), ZoneId.of(zoneId));
	}

	LentAzSearchPeriodResolver(Clock clock) {
		this(clock, clock.getZone());
	}

	LentAzSearchPeriodResolver(Clock clock, ZoneId zoneId) {
		this.clock = clock;
		this.zoneId = zoneId;
	}

	public int resolve(OffsetDateTime dateFrom, OffsetDateTime dateTo) {
		if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
			return ALL_TIME;
		}
		LocalDate today = LocalDate.now(clock.withZone(zoneId));
		LocalDate fromDate = dateFrom.atZoneSameInstant(zoneId).toLocalDate();
		if (!fromDate.isBefore(today.minusDays(7))) {
			return LAST_WEEK;
		}
		if (!fromDate.isBefore(today.minusMonths(1))) {
			return LAST_MONTH;
		}
		if (!fromDate.isBefore(today.minusMonths(6))) {
			return LAST_SIX_MONTHS;
		}
		return ALL_TIME;
	}
}
