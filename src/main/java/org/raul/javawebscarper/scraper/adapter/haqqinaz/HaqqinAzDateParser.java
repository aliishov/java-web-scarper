package org.raul.javawebscarper.scraper.adapter.haqqinaz;

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
public class HaqqinAzDateParser {

	private static final Pattern TIME_ONLY = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
	private static final Pattern RELATIVE_WITH_TIME = Pattern.compile("^(сегодня|вчера)(?:\\s*,?\\s*(\\d{1,2}):(\\d{2}))?$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern TIME_DOT_DATE = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*(?:,|-)?\\s*(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})$");
	private static final Pattern DOT_DATE_TIME = Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s*,?\\s*(\\d{1,2}):(\\d{2})$");
	private static final Pattern TIME_TEXT_DATE = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*,?\\s*(\\d{1,2})\\s+([а-яё]+)\\s+(\\d{4})$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern TEXT_DATE_TIME = Pattern.compile("^(\\d{1,2})\\s+([а-яё]+)\\s+(\\d{4})\\s*,?\\s*(\\d{1,2}):(\\d{2})$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
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
	public HaqqinAzDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId), Clock.system(ZoneId.of(zoneId)));
	}

	HaqqinAzDateParser(ZoneId zoneId) {
		this(zoneId, Clock.system(zoneId));
	}

	HaqqinAzDateParser(ZoneId zoneId, Clock clock) {
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
		return parseTimeOnly(normalized)
				.or(() -> parseRelativeDate(normalized))
				.or(() -> parseTimeDotDate(normalized))
				.or(() -> parseDotDateTime(normalized))
				.or(() -> parseTimeTextDate(normalized))
				.or(() -> parseTextDateTime(normalized))
				.or(() -> {
					log.warn("Unable to parse haqqin.az date: url={}, value={}", postUrl, value);
					return Optional.empty();
				});
	}

	private Optional<OffsetDateTime> parseTimeOnly(String value) {
		Matcher matcher = TIME_ONLY.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return dateTime(today(), matcher.group(1), matcher.group(2));
	}

	private Optional<OffsetDateTime> parseRelativeDate(String value) {
		Matcher matcher = RELATIVE_WITH_TIME.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		LocalDate date = today();
		if ("вчера".equals(matcher.group(1).toLowerCase(Locale.ROOT))) {
			date = date.minusDays(1);
		}
		String hour = matcher.group(2) == null ? "0" : matcher.group(2);
		String minute = matcher.group(3) == null ? "0" : matcher.group(3);
		return dateTime(date, hour, minute);
	}

	private Optional<OffsetDateTime> parseTimeDotDate(String value) {
		Matcher matcher = TIME_DOT_DATE.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return dateTime(matcher.group(5), matcher.group(4), matcher.group(3), matcher.group(1), matcher.group(2));
	}

	private Optional<OffsetDateTime> parseDotDateTime(String value) {
		Matcher matcher = DOT_DATE_TIME.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return dateTime(matcher.group(3), matcher.group(2), matcher.group(1), matcher.group(4), matcher.group(5));
	}

	private Optional<OffsetDateTime> parseTimeTextDate(String value) {
		Matcher matcher = TIME_TEXT_DATE.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return textMonthDateTime(matcher.group(5), matcher.group(4), matcher.group(3), matcher.group(1), matcher.group(2));
	}

	private Optional<OffsetDateTime> parseTextDateTime(String value) {
		Matcher matcher = TEXT_DATE_TIME.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return textMonthDateTime(matcher.group(3), matcher.group(2), matcher.group(1), matcher.group(4), matcher.group(5));
	}

	private Optional<OffsetDateTime> textMonthDateTime(String year, String monthName, String day, String hour, String minute) {
		Integer month = MONTHS.get(monthName.toLowerCase(Locale.ROOT));
		if (month == null) {
			return Optional.empty();
		}
		return dateTime(year, String.valueOf(month), day, hour, minute);
	}

	private Optional<OffsetDateTime> dateTime(String year, String month, String day, String hour, String minute) {
		try {
			return dateTime(
					LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)),
					hour,
					minute
			);
		} catch (NumberFormatException | DateTimeException exception) {
			return Optional.empty();
		}
	}

	private Optional<OffsetDateTime> dateTime(LocalDate date, String hour, String minute) {
		try {
			LocalDateTime dateTime = LocalDateTime.of(
					date,
					LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute))
			);
			return Optional.of(dateTime.atZone(clock.withZone(zoneId).getZone()).toOffsetDateTime());
		} catch (NumberFormatException | DateTimeException exception) {
			return Optional.empty();
		}
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(zoneId));
	}

	private String normalizeWhitespace(String value) {
		return value.replace('\u00A0', ' ')
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.trim();
	}
}
