package org.raul.javawebscarper.scraper.adapter.bakuws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class BakuWsDateParser {

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

	public BakuWsDateParser(@Value("${scraper.default-time-zone:Asia/Baku}") String zoneId) {
		this(ZoneId.of(zoneId));
	}

	public BakuWsDateParser(ZoneId zoneId) {
		this.zoneId = zoneId;
	}

	public Optional<OffsetDateTime> parseResultCardDate(String dayText, String timeText) {
		if (dayText == null || timeText == null) {
			return Optional.empty();
		}
		String[] parts = dayText.trim().split("\\s+");
		if (parts.length != 3) {
			log.warn("Unable to parse baku.ws result date: dayText={}, timeText={}", dayText, timeText);
			return Optional.empty();
		}
		return parseDateTime(parts[0], parts[1], parts[2], timeText);
	}

	public Optional<OffsetDateTime> parseArticleDate(
			String dayText,
			String monthText,
			String yearText,
			String timeText
	) {
		return parseDateTime(dayText, monthText, yearText, timeText);
	}

	private Optional<OffsetDateTime> parseDateTime(
			String dayText,
			String monthText,
			String yearText,
			String timeText
	) {
		try {
			int day = Integer.parseInt(dayText.trim());
			Integer month = MONTHS.get(monthText.trim().toLowerCase(Locale.ROOT));
			if (month == null) {
				log.warn("Unable to parse baku.ws month: {}", monthText);
				return Optional.empty();
			}
			int year = Integer.parseInt(yearText.trim());
			LocalTime time = LocalTime.parse(timeText.trim());
			return Optional.of(LocalDate.of(year, month, day)
					.atTime(time)
					.atZone(zoneId)
					.toOffsetDateTime());
		} catch (RuntimeException exception) {
			log.warn(
					"Unable to parse baku.ws date: day={}, month={}, year={}, time={}",
					dayText,
					monthText,
					yearText,
					timeText
			);
			return Optional.empty();
		}
	}
}
