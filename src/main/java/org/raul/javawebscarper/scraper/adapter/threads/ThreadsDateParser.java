package org.raul.javawebscarper.scraper.adapter.threads;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ThreadsDateParser {

	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Baku");
	private static final Pattern RELATIVE = Pattern.compile(
			"^(\\d+)\\s*(s|sec|m|min|h|hr|d|day|w|week)\\b.*$",
			Pattern.CASE_INSENSITIVE
	);

	private final Clock clock;

	public ThreadsDateParser() {
		this(Clock.system(DEFAULT_ZONE));
	}

	ThreadsDateParser(Clock clock) {
		this.clock = clock;
	}

	public Optional<OffsetDateTime> parse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = value.trim();
		try {
			return Optional.of(Instant.parse(normalized).atZone(clock.getZone()).toOffsetDateTime());
		} catch (DateTimeParseException ignored) {
			// Threads also renders compact relative timestamps.
		}
		try {
			return Optional.of(OffsetDateTime.parse(normalized));
		} catch (DateTimeParseException ignored) {
			return parseRelative(normalized);
		}
	}

	private Optional<OffsetDateTime> parseRelative(String value) {
		String normalized = value.toLowerCase(Locale.ROOT);
		if (normalized.equals("now") || normalized.equals("just now")) {
			return Optional.of(OffsetDateTime.now(clock));
		}
		Matcher matcher = RELATIVE.matcher(normalized);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		long amount = Long.parseLong(matcher.group(1));
		Duration duration = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
			case "s", "sec" -> Duration.ofSeconds(amount);
			case "m", "min" -> Duration.ofMinutes(amount);
			case "h", "hr" -> Duration.ofHours(amount);
			case "d", "day" -> Duration.ofDays(amount);
			case "w", "week" -> Duration.ofDays(amount * 7);
			default -> Duration.ZERO;
		};
		return Optional.of(OffsetDateTime.now(clock).minus(duration));
	}
}
