package org.raul.javawebscarper.scraper.adapter.oxuaz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OxuAzDateParser {

	private static final Pattern DATE_TIME_PATTERN = Pattern.compile(
			"(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s*(?:/\\s*)?(\\d{1,2}:\\d{2})"
	);
	private static final Pattern RELATIVE_DATE_PATTERN = Pattern.compile(
			"(.+?)\\s*/\\s*(\\d{1,2}:\\d{2})"
	);

	private final ZoneId zoneId;
	private final Clock clock;

	@Autowired
	public OxuAzDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId), Clock.system(ZoneId.of(zoneId)));
	}

	OxuAzDateParser(ZoneId zoneId) {
		this(zoneId, Clock.system(zoneId));
	}

	OxuAzDateParser(ZoneId zoneId, Clock clock) {
		this.zoneId = zoneId;
		this.clock = clock;
	}

	public Optional<OffsetDateTime> parseResultCardDate(String value) {
		return parseDateText(value);
	}

	public Optional<OffsetDateTime> parseArticleDate(String value) {
		return parseDateText(value);
	}

	private Optional<OffsetDateTime> parseDateText(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = normalizeWhitespace(value);
		Optional<OffsetDateTime> relativeDate = parseRelativeDate(normalized);
		if (relativeDate.isPresent()) {
			return relativeDate;
		}
		return parseAbsoluteDate(normalized);
	}

	private Optional<OffsetDateTime> parseRelativeDate(String value) {
		Matcher matcher = RELATIVE_DATE_PATTERN.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		String dayText = normalizeDayText(matcher.group(1));
		LocalDate date;
		if ("bugun".equals(dayText) || "bu gun".equals(dayText)) {
			date = LocalDate.now(clock.withZone(zoneId));
		} else if ("dunen".equals(dayText)) {
			date = LocalDate.now(clock.withZone(zoneId)).minusDays(1);
		} else {
			return Optional.empty();
		}
		try {
			return Optional.of(date.atTime(LocalTime.parse(normalizeTime(matcher.group(2))))
					.atZone(zoneId)
					.toOffsetDateTime());
		} catch (RuntimeException exception) {
			log.warn("Unable to parse oxu.az relative date time: value={}", value);
			return Optional.empty();
		}
	}

	private Optional<OffsetDateTime> parseAbsoluteDate(String value) {
		Matcher matcher = DATE_TIME_PATTERN.matcher(value);
		if (!matcher.find()) {
			log.warn("Unable to parse oxu.az date: value={}", value);
			return Optional.empty();
		}
		try {
			int day = Integer.parseInt(matcher.group(1));
			int month = Integer.parseInt(matcher.group(2));
			int year = Integer.parseInt(matcher.group(3));
			LocalTime time = LocalTime.parse(normalizeTime(matcher.group(4)));
			return Optional.of(LocalDate.of(year, month, day)
					.atTime(time)
					.atZone(zoneId)
					.toOffsetDateTime());
		} catch (RuntimeException exception) {
			log.warn("Unable to parse oxu.az absolute date: value={}", value);
			return Optional.empty();
		}
	}

	private String normalizeWhitespace(String value) {
		return value.replace('\u00A0', ' ')
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.trim();
	}

	private String normalizeTime(String value) {
		String trimmed = value.trim();
		return trimmed.length() == 4 ? "0" + trimmed : trimmed;
	}

	private String normalizeDayText(String value) {
		return normalizeWhitespace(value)
				.toLowerCase(Locale.ROOT)
				.replace("ü", "u")
				.replace("ə", "e")
				.replace("é™", "e")
				.replace("æ", "e");
	}
}
