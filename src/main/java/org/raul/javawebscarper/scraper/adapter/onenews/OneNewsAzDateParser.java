package org.raul.javawebscarper.scraper.adapter.onenews;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;

@Component
public class OneNewsAzDateParser {

	private final ZoneId zoneId;
	private final Clock clock;

	@Autowired
	public OneNewsAzDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId), Clock.system(ZoneId.of(zoneId)));
	}

	OneNewsAzDateParser(ZoneId zoneId) {
		this(zoneId, Clock.system(zoneId));
	}

	OneNewsAzDateParser(ZoneId zoneId, Clock clock) {
		this.zoneId = zoneId;
		this.clock = clock;
	}
}
