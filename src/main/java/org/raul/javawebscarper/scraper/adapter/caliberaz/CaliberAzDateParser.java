package org.raul.javawebscarper.scraper.adapter.caliberaz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CaliberAzDateParser {

	private static final Pattern TEXT_MONTH_DATE = Pattern.compile(
			"^(\\d{1,2})\\s+([а-яё]+)\\s+(\\d{4})\\s*(?:,|в)?\\s*(\\d{1,2}):(\\d{2})$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern DOT_DATE = Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s+(\\d{1,2}):(\\d{2})$");
	private static final Map<String, Integer> MONTHS = Map.ofEntries(
			Map.entry("января", 1),
			Map.entry("февраля", 2),
			Map.entry("марта", 3),
			Map.entry("апреля", 4),
			Map.entry("мая", 5),
			Map.entry("июня", 6),
			Map.entry("июля", 7),
			Map.entry("августа", 8),
			Map.entry("сентября", 9),
			Map.entry("октября", 10),
			Map.entry("ноября", 11),
			Map.entry("декабря", 12)
	);

	private final ZoneId zoneId;
	private final Clock clock;

	@Autowired
	public CaliberAzDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId), Clock.system(ZoneId.of(zoneId)));
	}

	CaliberAzDateParser(ZoneId zoneId) {
		this(zoneId, Clock.system(zoneId));
	}

	CaliberAzDateParser(ZoneId zoneId, Clock clock) {
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
		return parseTextMonthDate(normalized)
				.or(() -> parseDotDate(normalized))
				.or(() -> {
					log.warn("Unable to parse caliber.az date: url={}, value={}", postUrl, value);
					return Optional.empty();
				});
	}

	private Optional<OffsetDateTime> parseTextMonthDate(String value) {
		Matcher matcher = TEXT_MONTH_DATE.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		Integer month = MONTHS.get(matcher.group(2).toLowerCase(Locale.ROOT));
		if (month == null) {
			return Optional.empty();
		}
		return dateTime(matcher.group(3), String.valueOf(month), matcher.group(1), matcher.group(4), matcher.group(5));
	}

	private Optional<OffsetDateTime> parseDotDate(String value) {
		Matcher matcher = DOT_DATE.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return dateTime(matcher.group(3), matcher.group(2), matcher.group(1), matcher.group(4), matcher.group(5));
	}

	private Optional<OffsetDateTime> dateTime(String year, String month, String day, String hour, String minute) {
		try {
			LocalDateTime dateTime = LocalDateTime.of(
					LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)),
					LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute))
			);
			return Optional.of(dateTime.atZone(clock.withZone(zoneId).getZone()).toOffsetDateTime());
		} catch (NumberFormatException | DateTimeException exception) {
			return Optional.empty();
		}
	}

	private String normalizeWhitespace(String value) {
		return value.replace('\u00A0', ' ')
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.trim();
	}
}
