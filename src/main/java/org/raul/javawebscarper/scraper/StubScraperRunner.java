package org.raul.javawebscarper.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.config.ScraperEngineProperties;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.scraper.engine.ScraperEngine;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionException;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionStatus;
import org.raul.javawebscarper.scraper.support.ScraperClock;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StubScraperRunner implements ScraperRunner {

	private final ScraperEngine scraperEngine;
	private final ScraperEngineProperties properties;
	private final ScraperClock scraperClock;

	@Override
	public ScraperResult run(ScrapeJob job) {
		log.info(
				"Executing scraper engine for jobId={}, source={}, keyword={}, dateFrom={}, dateTo={}",
				job.getId(),
				job.getSource().getCode(),
				job.getKeyword().getWord(),
				job.getDateFrom(),
				job.getDateTo()
		);

		ScraperExecutionResult result = scraperEngine.execute(toExecutionContext(job));
		return toScraperResult(result);
	}

	private ScraperExecutionContext toExecutionContext(ScrapeJob job) {
		ZoneId zone = scraperClock.zone();
		OffsetDateTime dateFrom = job.getDateFrom()
				.atStartOfDay(zone)
				.toOffsetDateTime();
		OffsetDateTime dateTo = job.getDateTo()
				.plusDays(1)
				.atStartOfDay(zone)
				.minusNanos(1)
				.toOffsetDateTime();

		return new ScraperExecutionContext(
				job,
				job.getSource(),
				job.getKeyword(),
				dateFrom,
				dateTo,
				properties.getMaxPages(),
				properties.getMaxPosts(),
				Map.of("runner", getClass().getSimpleName())
		);
	}

	private ScraperResult toScraperResult(ScraperExecutionResult result) {
		if (result.status() == ScraperExecutionStatus.FAILED) {
			throw new ScraperExecutionException(result.errorMessage());
		}
		if (result.status() == ScraperExecutionStatus.UNSUPPORTED && properties.isFailOnUnsupportedSource()) {
			throw new ScraperExecutionException(result.errorMessage());
		}
		if (result.status() == ScraperExecutionStatus.EMPTY || result.status() == ScraperExecutionStatus.UNSUPPORTED) {
			return ScraperResult.empty();
		}
		return new ScraperResult(result.posts().size(), 0, List.of());
	}
}
