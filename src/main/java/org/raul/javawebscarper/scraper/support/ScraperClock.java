package org.raul.javawebscarper.scraper.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class ScraperClock {

	private final Clock clock;

	public OffsetDateTime now() {
		return OffsetDateTime.now(clock);
	}

	public ZoneId zone() {
		return clock.getZone();
	}
}
