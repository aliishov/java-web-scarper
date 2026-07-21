package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TikTokDateParser {

	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Baku");
	private static final Pattern EPOCH = Pattern.compile("^\\d{10}(?:\\d{3})?$");
	private static final Pattern RELATIVE = Pattern.compile(
			"^(\\d+)\\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|week|weeks|mo|month|months|y|yr|year|years|с|сек|м|мин|ч|час|д|дн|нед|месяц|мес|г|год|gün|gun|saat|dəq|deq|dk|hafta|ay|il)\\b.*$",
			Pattern.CASE_INSENSITIVE
	);
	private static final List<DateTimeFormatter> LOCAL_DATE_TIME_FORMATTERS = List.of(
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
			DateTimeFormatter.ofPattern("MM-dd HH:mm")
	);
	private static final List<DateTimeFormatter> LOCAL_DATE_FORMATTERS = List.of(
			DateTimeFormatter.ISO_LOCAL_DATE,
			DateTimeFormatter.ofPattern("dd.MM.yyyy"),
			new DateTimeFormatterBuilder()
					.appendPattern("M-d")
					.parseDefaulting(ChronoField.YEAR, 1970)
					.toFormatter(Locale.ROOT)
	);

	private final ZoneId zoneId;
	private final Clock clock;

	public TikTokDateParser() {
		this(DEFAULT_ZONE, Clock.system(DEFAULT_ZONE));
	}

	TikTokDateParser(ZoneId zoneId, Clock clock) {
		this.zoneId = zoneId == null ? DEFAULT_ZONE : zoneId;
		this.clock = clock == null ? Clock.system(this.zoneId) : clock;
	}

	public Optional<OffsetDateTime> parse(String value) {
		return parseWithSource(value).map(TikTokParsedDate::value);
	}

	public Optional<TikTokParsedDate> parseWithSource(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = normalize(value);
		return parseEpoch(normalized)
				.or(() -> parseInstant(normalized))
				.or(() -> parseOffsetDateTime(normalized))
				.or(() -> parseAbsoluteLocalDateTime(normalized))
				.or(() -> parseAbsoluteLocalDate(normalized))
				.or(() -> parseRelative(normalized));
	}

	private Optional<TikTokParsedDate> parseEpoch(String value) {
		if (!EPOCH.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			long raw = Long.parseLong(value);
			Instant instant = value.length() == 13 ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
			return Optional.of(new TikTokParsedDate(instant.atZone(zoneId).toOffsetDateTime(), "MACHINE_READABLE"));
		} catch (RuntimeException exception) {
			return Optional.empty();
		}
	}

	private Optional<TikTokParsedDate> parseInstant(String value) {
		try {
			return Optional.of(new TikTokParsedDate(Instant.parse(value).atZone(zoneId).toOffsetDateTime(), "ISO_INSTANT"));
		} catch (DateTimeParseException exception) {
			return Optional.empty();
		}
	}

	private Optional<TikTokParsedDate> parseOffsetDateTime(String value) {
		try {
			return Optional.of(new TikTokParsedDate(OffsetDateTime.parse(value), "OFFSET_DATETIME"));
		} catch (DateTimeParseException exception) {
			return Optional.empty();
		}
	}

	private Optional<TikTokParsedDate> parseAbsoluteLocalDateTime(String value) {
		for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
			try {
				LocalDateTime localDateTime = LocalDateTime.parse(value, formatter);
				return Optional.of(new TikTokParsedDate(localDateTime.atZone(zoneId).toOffsetDateTime(), "VISIBLE_ABSOLUTE"));
			} catch (DateTimeParseException ignored) {
				// Try the next known TikTok visible format.
			}
		}
		return Optional.empty();
	}

	private Optional<TikTokParsedDate> parseAbsoluteLocalDate(String value) {
		for (DateTimeFormatter formatter : LOCAL_DATE_FORMATTERS) {
			try {
				LocalDate localDate = LocalDate.parse(value, formatter);
				if (localDate.getYear() == 1970) {
					localDate = localDate.withYear(OffsetDateTime.now(clock).getYear());
				}
				return Optional.of(new TikTokParsedDate(localDate.atStartOfDay(zoneId).toOffsetDateTime(), "VISIBLE_ABSOLUTE"));
			} catch (DateTimeParseException ignored) {
				// Try the next known TikTok visible format.
			}
		}
		return Optional.empty();
	}

	private Optional<TikTokParsedDate> parseRelative(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		if (normalized.equals("now") || normalized.equals("just now") || normalized.equals("сейчас") || normalized.equals("indi")) {
			return Optional.of(new TikTokParsedDate(OffsetDateTime.now(clock), "VISIBLE_RELATIVE"));
		}
		Matcher matcher = RELATIVE.matcher(normalized);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		long amount = Long.parseLong(matcher.group(1));
		Duration duration = duration(amount, matcher.group(2).toLowerCase(Locale.ROOT));
		if (duration.isZero()) {
			return Optional.empty();
		}
		return Optional.of(new TikTokParsedDate(OffsetDateTime.now(clock).minus(duration), "VISIBLE_RELATIVE"));
	}

	private Duration duration(long amount, String unit) {
		return switch (unit) {
			case "s", "sec", "secs", "second", "seconds", "с", "сек" -> Duration.ofSeconds(amount);
			case "m", "min", "mins", "minute", "minutes", "м", "мин", "dəq", "deq", "dk" -> Duration.ofMinutes(amount);
			case "h", "hr", "hrs", "hour", "hours", "ч", "час", "saat" -> Duration.ofHours(amount);
			case "d", "day", "days", "д", "дн", "gün", "gun" -> Duration.ofDays(amount);
			case "w", "week", "weeks", "нед", "hafta" -> Duration.ofDays(amount * 7);
			case "mo", "month", "months", "месяц", "мес", "ay" -> Duration.ofDays(amount * 30);
			case "y", "yr", "year", "years", "г", "год", "il" -> Duration.ofDays(amount * 365);
			default -> Duration.ZERO;
		};
	}

	private String normalize(String value) {
		return value
				.replace('\u00a0', ' ')
				.replace("·", " ")
				.replace("|", " ")
				.replaceAll("(?i)\\bago\\b", "")
				.replaceAll("\\s+", " ")
				.trim();
	}
}
