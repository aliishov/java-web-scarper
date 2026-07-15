package org.raul.javawebscarper.scraper.adapter.xcom;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class XComDateParser {

	public Optional<OffsetDateTime> parseDatetime(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(OffsetDateTime.parse(value.trim()));
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
	}
}
