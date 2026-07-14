package org.raul.javawebscarper.scraper.adapter.lentaz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LentAzDateParser {

	private static final Pattern DATE_TIME = Pattern.compile(
			"^(?:(?<timeFirst>\\d{1,2}:\\d{2})\\s+)?(?<day>\\d{1,2})\\s+(?<month>\\p{L}+)\\s+(?<year>\\d{4})(?:,?\\s*(?<timeAfter>\\d{1,2}:\\d{2}))?(?:\\s*\\(?\\s*UTC\\s*(?<offset>[+-]\\d{2}:?\\d{2})\\s*\\)?)?$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern TIME = Pattern.compile("^\\d{1,2}:\\d{2}$");
	private static final Map<String, Integer> MONTHS = Map.ofEntries(
			Map.entry("yanvar", 1),
			Map.entry("yan", 1),
			Map.entry("fevral", 2),
			Map.entry("fev", 2),
			Map.entry("mart", 3),
			Map.entry("mar", 3),
			Map.entry("aprel", 4),
			Map.entry("apr", 4),
			Map.entry("may", 5),
			Map.entry("iyun", 6),
			Map.entry("iyn", 6),
			Map.entry("iyul", 7),
			Map.entry("iyl", 7),
			Map.entry("avqust", 8),
			Map.entry("avq", 8),
			Map.entry("sentyabr", 9),
			Map.entry("sen", 9),
			Map.entry("oktyabr", 10),
			Map.entry("okt", 10),
			Map.entry("noyabr", 11),
			Map.entry("noy", 11),
			Map.entry("dekabr", 12),
			Map.entry("dek", 12)
	);

	private final ZoneId zoneId;
	private final Clock clock;

	@Autowired
	public LentAzDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId), Clock.system(ZoneId.of(zoneId)));
	}

	LentAzDateParser(ZoneId zoneId) {
		this(zoneId, Clock.system(zoneId));
	}

	LentAzDateParser(ZoneId zoneId, Clock clock) {
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
		Optional<OffsetDateTime> parsed = parseNormalized(normalize(value));
		if (parsed.isEmpty()) {
			log.warn("Unable to parse lent.az article date: url={}, value={}", postUrl, value);
		}
		return parsed;
	}

	public Optional<OffsetDateTime> parseSearchCardDate(String dateText, String timeText, String postUrl) {
		if (dateText == null || dateText.isBlank() || timeText == null || timeText.isBlank()) {
			return Optional.empty();
		}
		String combined = normalize(dateText + " " + timeText);
		Optional<OffsetDateTime> parsed = parseNormalized(combined);
		if (parsed.isEmpty()) {
			log.warn("Unable to parse lent.az search card date: url={}, date={}, time={}", postUrl, dateText, timeText);
		}
		return parsed;
	}

	public boolean isTimeText(String value) {
		return value != null && TIME.matcher(value.trim()).matches();
	}

	private Optional<OffsetDateTime> parseNormalized(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = DATE_TIME.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		try {
			int day = Integer.parseInt(matcher.group("day"));
			int month = MONTHS.getOrDefault(matcher.group("month").toLowerCase(Locale.ROOT), -1);
			int year = Integer.parseInt(matcher.group("year"));
			String timeValue = firstNonBlank(matcher.group("timeAfter"), matcher.group("timeFirst"));
			if (month < 1 || timeValue == null) {
				return Optional.empty();
			}
			String[] timeParts = timeValue.split(":");
			LocalDate date = LocalDate.of(year, month, day);
			LocalTime time = LocalTime.of(Integer.parseInt(timeParts[0]), Integer.parseInt(timeParts[1]));
			String offsetValue = matcher.group("offset");
			if (offsetValue != null && !offsetValue.isBlank()) {
				return Optional.of(OffsetDateTime.of(date, time, parseOffset(offsetValue)));
			}
			return Optional.of(date.atTime(time).atZone(zoneId).toOffsetDateTime());
		} catch (DateTimeException | NumberFormatException exception) {
			return Optional.empty();
		}
	}

	private ZoneOffset parseOffset(String value) {
		String normalized = value.trim();
		if (normalized.matches("[+-]\\d{4}")) {
			normalized = normalized.substring(0, 3) + ":" + normalized.substring(3);
		}
		return ZoneOffset.of(normalized);
	}

	private String normalize(String value) {
		return value == null ? null : value
				.replace('\u00A0', ' ')
				.replace("UTC+", "UTC +")
				.replace("UTC-", "UTC -")
				.replaceAll("\\s+", " ")
				.trim()
				.toLowerCase(Locale.ROOT);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}
