package org.raul.javawebscarper.scraper.adapter.mediaaz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Slf4j
@Component
public class MediaAzDateParser {

	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter VISIBLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

	private final ZoneId zoneId;
	private final Clock clock;

	@Autowired
	public MediaAzDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId), Clock.system(ZoneId.of(zoneId)));
	}

	MediaAzDateParser(ZoneId zoneId) {
		this(zoneId, Clock.system(zoneId));
	}

	MediaAzDateParser(ZoneId zoneId, Clock clock) {
		this.zoneId = zoneId;
		this.clock = clock;
	}

	public Optional<OffsetDateTime> parseResultCardTimestamp(String value) {
		return parse(value, TIMESTAMP_FORMATTER);
	}

	public Optional<OffsetDateTime> parseVisibleDate(String value) {
		return parse(value, VISIBLE_DATE_FORMATTER);
	}

	public Optional<OffsetDateTime> parseArticleDate(String value) {
		return parseVisibleDate(value);
	}

	private Optional<OffsetDateTime> parse(String value, DateTimeFormatter formatter) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = normalizeWhitespace(value);
		try {
			return Optional.of(LocalDateTime.parse(normalized, formatter)
					.atZone(clock.withZone(zoneId).getZone())
					.toOffsetDateTime());
		} catch (DateTimeParseException exception) {
			log.warn("Unable to parse media.az date: value={}", value);
			return Optional.empty();
		}
	}

	private String normalizeWhitespace(String value) {
		return value.replace('\u00A0', ' ')
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.trim();
	}
}
