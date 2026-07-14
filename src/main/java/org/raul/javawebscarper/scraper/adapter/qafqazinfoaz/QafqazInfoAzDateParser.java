package org.raul.javawebscarper.scraper.adapter.qafqazinfoaz;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
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

@Slf4j
@Component
public class QafqazInfoAzDateParser {

	private static final Pattern DATE_PATTERN = Pattern.compile(
			"(?<date>\\d{1,2}[.\\-]\\d{1,2}[.\\-]\\d{4})\\s*(?:\\|)?\\s*(?<time>\\d{1,2}:\\d{2})"
	);
	private static final List<DateTimeFormatter> VISIBLE_FORMATTERS = List.of(
			formatter("d.M.uuuu H:mm"),
			formatter("d-M-uuuu H:mm")
	);
	private static final DateTimeFormatter DATETIME_FORMATTER = formatter("M.d.uuuu H:mm");

	private final ZoneId zoneId;

	@Autowired
	public QafqazInfoAzDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId));
	}

	QafqazInfoAzDateParser(ZoneId zoneId) {
		this(zoneId, Clock.system(zoneId));
	}

	QafqazInfoAzDateParser(ZoneId zoneId, Clock clock) {
		this.zoneId = zoneId;
	}

	public Optional<DateParseResult> parseArticleDate(String visibleText, String datetimeText, String postUrl) {
		Optional<OffsetDateTime> visibleDate = parseVisibleDate(visibleText);
		Optional<OffsetDateTime> datetimeDate = parseDatetimeAttribute(datetimeText);
		if (visibleDate.isPresent()) {
			boolean conflict = datetimeDate.isPresent() && !visibleDate.get().toLocalDate().equals(datetimeDate.get().toLocalDate());
			if (conflict) {
				log.warn(
						"qafqazinfo.az article date conflict: url={}, visible={}, datetime={}",
						postUrl,
						visibleDate.get(),
						datetimeDate.get()
				);
			}
			return Optional.of(new DateParseResult(visibleDate.get(), conflict, "visible"));
		}
		if (datetimeDate.isPresent()) {
			return Optional.of(new DateParseResult(datetimeDate.get(), false, "datetime"));
		}
		if ((visibleText != null && !visibleText.isBlank()) || (datetimeText != null && !datetimeText.isBlank())) {
			log.warn("Unable to parse qafqazinfo.az date: url={}, visible={}, datetime={}", postUrl, visibleText, datetimeText);
		}
		return Optional.empty();
	}

	public Optional<OffsetDateTime> parseVisibleDate(String value) {
		return parse(value, VISIBLE_FORMATTERS);
	}

	public Optional<OffsetDateTime> parseDatetimeAttribute(String value) {
		return parse(normalize(value), List.of(DATETIME_FORMATTER));
	}

	private Optional<OffsetDateTime> parse(String value, List<DateTimeFormatter> formatters) {
		String normalized = normalize(value);
		if (normalized == null) {
			return Optional.empty();
		}
		for (DateTimeFormatter formatter : formatters) {
			try {
				return Optional.of(LocalDateTime.parse(normalized, formatter).atZone(zoneId).toOffsetDateTime());
			} catch (DateTimeParseException ignored) {
				// Try the next supported site-specific format.
			}
		}
		return Optional.empty();
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		Matcher matcher = DATE_PATTERN.matcher(value.trim().replace('\u00A0', ' '));
		if (!matcher.find()) {
			return value.trim().replaceAll("\\s+", " ");
		}
		return matcher.group("date").replace('-', '.') + " " + matcher.group("time");
	}

	private static DateTimeFormatter formatter(String pattern) {
		return new DateTimeFormatterBuilder()
				.parseCaseInsensitive()
				.appendPattern(pattern)
				.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
				.toFormatter(Locale.ROOT);
	}

	record DateParseResult(OffsetDateTime date, boolean conflict, String source) {
	}
}
