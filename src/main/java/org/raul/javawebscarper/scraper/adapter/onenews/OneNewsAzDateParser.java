package org.raul.javawebscarper.scraper.adapter.onenews;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OneNewsAzDateParser {

	private static final Pattern ARTICLE_DATE_PATTERN = Pattern.compile(
			"(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*/\\s*(\\d{4})"
	);

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

	public Optional<OffsetDateTime> parseArticleDate(String value) {
		return parseArticleDate(value, null);
	}

	public Optional<OffsetDateTime> parseArticleDate(String value, String postUrl) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = normalizeWhitespace(value);
		Matcher matcher = ARTICLE_DATE_PATTERN.matcher(normalized);
		if (!matcher.find()) {
			log.warn("Unable to parse 1news.az date: url={}, value={}", postUrl, value);
			return Optional.empty();
		}
		try {
			int hour = Integer.parseInt(matcher.group(1));
			int minute = Integer.parseInt(matcher.group(2));
			int day = Integer.parseInt(matcher.group(3));
			int month = Integer.parseInt(matcher.group(4));
			int year = Integer.parseInt(matcher.group(5));

			LocalDateTime dateTime = LocalDateTime.of(
					LocalDate.of(year, month, day),
					LocalTime.of(hour, minute)
			);
			return Optional.of(dateTime.atZone(clock.withZone(zoneId).getZone()).toOffsetDateTime());
		} catch (NumberFormatException | DateTimeException exception) {
			log.warn("Unable to parse 1news.az date: url={}, value={}", postUrl, value);
			return Optional.empty();
		}
	}

	private String normalizeWhitespace(String value) {
		return value.replace('\u00A0', ' ')
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.trim();
	}
}
