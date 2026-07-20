package org.raul.javawebscarper.scraper.adapter.instagram;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class InstagramDateParser {

	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Baku");
	private static final Pattern RELATIVE = Pattern.compile("^(\\d+)\\s*(m|min|minute|minutes|h|hr|hour|hours|d|day|days|w|week|weeks)\\b.*$", Pattern.CASE_INSENSITIVE);
	private static final List<DateTimeFormatter> ABSOLUTE_FORMATTERS = List.of(
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
	);

	private final ZoneId zoneId;
	private final Clock clock;

	public InstagramDateParser() {
		this(DEFAULT_ZONE, Clock.system(DEFAULT_ZONE));
	}

	InstagramDateParser(ZoneId zoneId, Clock clock) {
		this.zoneId = zoneId == null ? DEFAULT_ZONE : zoneId;
		this.clock = clock == null ? Clock.system(this.zoneId) : clock;
	}

	public Optional<OffsetDateTime> parse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = value.trim();
		return parseInstant(normalized)
				.or(() -> parseOffsetDateTime(normalized))
				.or(() -> parseAbsoluteLocalDateTime(normalized))
				.or(() -> parseRelative(normalized));
	}

	private Optional<OffsetDateTime> parseInstant(String value) {
		try {
			return Optional.of(Instant.parse(value).atZone(zoneId).toOffsetDateTime());
		} catch (DateTimeParseException exception) {
			return Optional.empty();
		}
	}

	private Optional<OffsetDateTime> parseOffsetDateTime(String value) {
		try {
			return Optional.of(OffsetDateTime.parse(value));
		} catch (DateTimeParseException exception) {
			return Optional.empty();
		}
	}

	private Optional<OffsetDateTime> parseAbsoluteLocalDateTime(String value) {
		for (DateTimeFormatter formatter : ABSOLUTE_FORMATTERS) {
			try {
				LocalDateTime localDateTime = LocalDateTime.parse(value, formatter);
				return Optional.of(localDateTime.atZone(zoneId).toOffsetDateTime());
			} catch (DateTimeParseException ignored) {
				// Try the next stable formatter.
			}
		}
		return Optional.empty();
	}

	private Optional<OffsetDateTime> parseRelative(String value) {
		String normalized = value.toLowerCase(Locale.ROOT).trim();
		if (normalized.equals("now") || normalized.equals("just now")) {
			return Optional.of(OffsetDateTime.now(clock));
		}
		Matcher matcher = RELATIVE.matcher(normalized);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		long amount = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2).toLowerCase(Locale.ROOT);
		Duration duration = switch (unit) {
			case "m", "min", "minute", "minutes" -> Duration.ofMinutes(amount);
			case "h", "hr", "hour", "hours" -> Duration.ofHours(amount);
			case "d", "day", "days" -> Duration.ofDays(amount);
			case "w", "week", "weeks" -> Duration.ofDays(amount * 7);
			default -> Duration.ZERO;
		};
		return Optional.of(OffsetDateTime.now(clock).minus(duration));
	}
}
