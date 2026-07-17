package org.raul.javawebscarper.scraper.adapter.facebook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FacebookDateParser {

	private static final DateTimeFormatter NUMERIC_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
	private static final DateTimeFormatter NUMERIC_DATE_TIME_WITH_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	private static final DateTimeFormatter ENGLISH_MONTH_DATE = new DateTimeFormatterBuilder()
			.parseCaseInsensitive()
			.appendPattern("MMMM d, yyyy")
			.optionalStart()
			.appendLiteral(" at ")
			.appendPattern("h:mm a")
			.optionalEnd()
			.toFormatter(Locale.ENGLISH);
	private static final Pattern UNIX_TIMESTAMP = Pattern.compile("^\\d{10,13}$");
	private static final Pattern RELATIVE = Pattern.compile("(?:about\\s+)?(\\d+)\\s*([\\p{L}.]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern TIME = Pattern.compile("(?:at|в)?\\s*(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern DAY_MONTH = Pattern.compile(
			"\\b(\\d{1,2})\\s+([\\p{L}.]+)(?:[,]?\\s+(\\d{4}))?(?:\\s+(?:at|в)?\\s*(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?)?",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Map<String, Integer> MONTHS = Map.ofEntries(
			Map.entry("january", 1), Map.entry("jan", 1), Map.entry("января", 1), Map.entry("январь", 1), Map.entry("янв", 1), Map.entry("yanvar", 1),
			Map.entry("february", 2), Map.entry("feb", 2), Map.entry("февраля", 2), Map.entry("февраль", 2), Map.entry("фев", 2), Map.entry("fevral", 2),
			Map.entry("march", 3), Map.entry("mar", 3), Map.entry("марта", 3), Map.entry("март", 3), Map.entry("мар", 3), Map.entry("mart", 3),
			Map.entry("april", 4), Map.entry("apr", 4), Map.entry("апреля", 4), Map.entry("апрель", 4), Map.entry("апр", 4), Map.entry("aprel", 4),
			Map.entry("may", 5), Map.entry("мая", 5), Map.entry("mayis", 5), Map.entry("mayıs", 5),
			Map.entry("june", 6), Map.entry("jun", 6), Map.entry("июня", 6), Map.entry("июнь", 6), Map.entry("июн", 6), Map.entry("iyun", 6),
			Map.entry("july", 7), Map.entry("jul", 7), Map.entry("июля", 7), Map.entry("июль", 7), Map.entry("июл", 7), Map.entry("iyul", 7),
			Map.entry("august", 8), Map.entry("aug", 8), Map.entry("августа", 8), Map.entry("август", 8), Map.entry("авг", 8), Map.entry("avqust", 8),
			Map.entry("september", 9), Map.entry("sep", 9), Map.entry("sept", 9), Map.entry("сентября", 9), Map.entry("сентябрь", 9), Map.entry("сен", 9), Map.entry("sentyabr", 9),
			Map.entry("october", 10), Map.entry("oct", 10), Map.entry("октября", 10), Map.entry("октябрь", 10), Map.entry("окт", 10), Map.entry("oktyabr", 10),
			Map.entry("november", 11), Map.entry("nov", 11), Map.entry("ноября", 11), Map.entry("ноябрь", 11), Map.entry("ноя", 11), Map.entry("noyabr", 11),
			Map.entry("december", 12), Map.entry("dec", 12), Map.entry("декабря", 12), Map.entry("декабрь", 12), Map.entry("дек", 12), Map.entry("dekabr", 12)
	);

	private final ZoneId zoneId;
	private final Clock clock;

	@Autowired
	public FacebookDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId), Clock.system(ZoneId.of(zoneId)));
	}

	FacebookDateParser(ZoneId zoneId, Clock clock) {
		this.zoneId = zoneId;
		this.clock = clock.withZone(zoneId);
	}

	public Optional<OffsetDateTime> parse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = normalize(value);
		if (normalized.isBlank() || isMediaTooltip(normalized)) {
			return Optional.empty();
		}
		return parseUnixTimestamp(normalized)
				.or(() -> parseIso(normalized))
				.or(() -> parseNumericDateTime(normalized))
				.or(() -> parseEnglishAbsolute(normalized))
				.or(() -> parseTodayYesterday(normalized))
				.or(() -> parseRelative(normalized))
				.or(() -> parseLocalizedAbsolute(normalized));
	}

	public boolean looksLikeDate(String value) {
		return parse(value).isPresent();
	}

	private Optional<OffsetDateTime> parseUnixTimestamp(String value) {
		if (!UNIX_TIMESTAMP.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			long timestamp = Long.parseLong(value);
			Instant instant = Instant.ofEpochMilli(timestamp < 1_000_000_000_000L ? timestamp * 1_000L : timestamp);
			return Optional.of(instant.atZone(zoneId).toOffsetDateTime());
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
	}

	private Optional<OffsetDateTime> parseIso(String value) {
		try {
			return Optional.of(OffsetDateTime.parse(value).atZoneSameInstant(zoneId).toOffsetDateTime());
		} catch (DateTimeParseException ignored) {
			try {
				return Optional.of(LocalDateTime.parse(value.replace(' ', 'T')).atZone(zoneId).toOffsetDateTime());
			} catch (DateTimeParseException ignoredAgain) {
				return Optional.empty();
			}
		}
	}

	private Optional<OffsetDateTime> parseNumericDateTime(String value) {
		String normalized = value.replace('/', '.');
		try {
			return Optional.of(LocalDateTime.parse(normalized, NUMERIC_DATE_TIME)
					.atZone(zoneId)
					.toOffsetDateTime());
		} catch (DateTimeParseException ignored) {
			try {
				return Optional.of(LocalDateTime.parse(value, NUMERIC_DATE_TIME_WITH_SLASH)
						.atZone(zoneId)
						.toOffsetDateTime());
			} catch (DateTimeParseException ignoredAgain) {
				return Optional.empty();
			}
		}
	}

	private Optional<OffsetDateTime> parseEnglishAbsolute(String value) {
		String cleaned = value.replaceFirst("(?i)^(monday|tuesday|wednesday|thursday|friday|saturday|sunday),?\\s+", "");
		try {
			LocalDateTime parsed = LocalDateTime.parse(cleaned, ENGLISH_MONTH_DATE);
			return Optional.of(parsed.atZone(zoneId).toOffsetDateTime());
		} catch (DateTimeParseException ignored) {
			try {
				LocalDate date = LocalDate.parse(cleaned, ENGLISH_MONTH_DATE);
				return Optional.of(date.atStartOfDay(zoneId).toOffsetDateTime());
			} catch (DateTimeParseException ignoredAgain) {
				return Optional.empty();
			}
		}
	}

	private Optional<OffsetDateTime> parseTodayYesterday(String value) {
		String lower = stripDots(value.toLowerCase(Locale.ROOT));
		LocalDate date;
		if (lower.startsWith("today") || lower.startsWith("сегодня") || lower.startsWith("bu gün") || lower.startsWith("bu gun")) {
			date = LocalDate.now(clock);
		} else if (lower.startsWith("yesterday") || lower.startsWith("вчера") || lower.startsWith("dünən") || lower.startsWith("dunen")) {
			date = LocalDate.now(clock).minusDays(1);
		} else if (lower.equals("just now") || lower.equals("сейчас") || lower.equals("только что") || lower.equals("indi")) {
			return Optional.of(OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
		} else {
			return Optional.empty();
		}
		return Optional.of(date.atTime(extractTime(value).orElse(LocalTime.MIDNIGHT))
				.atZone(zoneId)
				.toOffsetDateTime());
	}

	private Optional<OffsetDateTime> parseRelative(String value) {
		Matcher matcher = RELATIVE.matcher(stripDots(value.toLowerCase(Locale.ROOT)));
		if (!matcher.find()) {
			return Optional.empty();
		}
		int amount = Integer.parseInt(matcher.group(1));
		String unit = matcher.group(2);
		OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		if (unit.matches("^(s|sec|secs|second|seconds|сек|секунд|saniyə|saniye)$")) {
			return Optional.of(now.minusSeconds(amount));
		}
		if (unit.matches("^(m|min|mins|minute|minutes|мин|минут|dəq|deq|dəqiqə|deqiqe)$")) {
			return Optional.of(now.minusMinutes(amount));
		}
		if (unit.matches("^(h|hr|hrs|hour|hours|ч|час|saat)$")) {
			return Optional.of(now.minusHours(amount));
		}
		if (unit.matches("^(d|day|days|д|дн|день|дня|дней|gün|gun)$")) {
			return Optional.of(now.minusDays(amount));
		}
		if (unit.matches("^(w|wk|wks|week|weeks|нед|неделя|недели|həftə|hefte)$")) {
			return Optional.of(now.minusWeeks(amount));
		}
		if (unit.matches("^(mo|mon|month|months|мес|месяц|месяца|месяцев|ay)$")) {
			return Optional.of(now.minusMonths(amount));
		}
		if (unit.matches("^(y|yr|yrs|year|years|г|год|года|лет|il)$")) {
			return Optional.of(now.minusYears(amount));
		}
		return Optional.empty();
	}

	private Optional<OffsetDateTime> parseLocalizedAbsolute(String value) {
		Matcher matcher = DAY_MONTH.matcher(value.toLowerCase(Locale.ROOT));
		if (!matcher.find()) {
			return Optional.empty();
		}
		String monthName = stripDots(matcher.group(2));
		Integer month = MONTHS.get(monthName);
		if (month == null) {
			return Optional.empty();
		}
		int day = Integer.parseInt(matcher.group(1));
		int year = matcher.group(3) == null ? LocalDate.now(clock).getYear() : Integer.parseInt(matcher.group(3));
		int hour = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
		int minute = matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5));
		if ("pm".equalsIgnoreCase(matcher.group(6)) && hour < 12) {
			hour += 12;
		}
		if ("am".equalsIgnoreCase(matcher.group(6)) && hour == 12) {
			hour = 0;
		}
		try {
			OffsetDateTime parsed = LocalDate.of(year, month, day)
					.atTime(hour, minute)
					.atZone(zoneId)
					.toOffsetDateTime();
			if (matcher.group(3) == null && parsed.isAfter(OffsetDateTime.now(clock))) {
				return Optional.of(parsed.minusYears(1));
			}
			return Optional.of(parsed);
		} catch (RuntimeException exception) {
			log.warn("Unable to parse Facebook localized date: {}", value);
			return Optional.empty();
		}
	}

	private Optional<LocalTime> extractTime(String value) {
		Matcher matcher = TIME.matcher(value);
		if (!matcher.find()) {
			return Optional.empty();
		}
		int hour = Integer.parseInt(matcher.group(1));
		int minute = Integer.parseInt(matcher.group(2));
		if ("pm".equalsIgnoreCase(matcher.group(3)) && hour < 12) {
			hour += 12;
		}
		if ("am".equalsIgnoreCase(matcher.group(3)) && hour == 12) {
			hour = 0;
		}
		return Optional.of(LocalTime.of(hour, minute));
	}

	private String normalize(String value) {
		return value.replace('\u00A0', ' ')
				.replace("\u200e", "")
				.replace("\u200f", "")
				.replace("/", " ")
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.trim();
	}

	private String stripDots(String value) {
		return value.replace(".", "").trim();
	}

	private boolean isMediaTooltip(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		return normalized.contains("may be an image")
				|| normalized.contains("may be a video")
				|| normalized.contains("image may contain")
				|| normalized.contains("video may contain")
				|| normalized.contains("может быть изображение")
				|| normalized.contains("может быть видео")
				|| normalized.contains("на изображении может быть")
				|| normalized.contains("на видео может быть");
	}
}
