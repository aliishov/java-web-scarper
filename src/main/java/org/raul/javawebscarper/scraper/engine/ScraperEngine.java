package org.raul.javawebscarper.scraper.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.scraper.adapter.ScraperAdapter;
import org.raul.javawebscarper.scraper.registry.ScraperAdapterRegistry;
import org.raul.javawebscarper.scraper.support.DateRangeValidator;
import org.raul.javawebscarper.scraper.support.ScraperClock;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperEngine {

	private final ScraperAdapterRegistry scraperAdapterRegistry;
	private final ScraperClock scraperClock;

	public ScraperExecutionResult execute(ScraperExecutionContext context) {
		OffsetDateTime startedAt = scraperClock.now();
		try {
			validateContext(context);
			ScraperAdapter adapter = scraperAdapterRegistry.getAdapter(context.source());
			String sourceCode = context.source().getCode();
			String keyword = context.keyword().getWord();

			log.info(
					"Starting scraper engine execution: jobId={}, source={}, keyword={}, dateFrom={}, dateTo={}, adapter={}",
					context.job().getId(),
					sourceCode,
					keyword,
					context.dateFrom(),
					context.dateTo(),
					adapter.sourceCode()
			);

			ScraperExecutionResult result = adapter.scrape(context);
			ScraperExecutionResult timedResult = withTiming(result, startedAt);

			log.info(
					"Scraper engine execution finished: jobId={}, source={}, keyword={}, status={}, postsFound={}, postsSkipped={}",
					context.job().getId(),
					sourceCode,
					keyword,
					timedResult.status(),
					timedResult.postsFound(),
					timedResult.postsSkipped()
			);
			return timedResult;
		} catch (ScraperExecutionException exception) {
			log.warn("Scraper engine execution failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed(exception.getMessage())
					.withTiming(startedAt, scraperClock.now());
		} catch (Exception exception) {
			log.error("Unexpected scraper engine execution failure", exception);
			return ScraperExecutionResult.failed("Unexpected scraper engine error: " + exception.getMessage())
					.withTiming(startedAt, scraperClock.now());
		}
	}

	private void validateContext(ScraperExecutionContext context) {
		if (context == null) {
			throw new ScraperExecutionException("Scraper execution context must not be null");
		}
		if (context.job() == null) {
			throw new ScraperExecutionException("ScrapeJob must not be null");
		}
		if (context.source() == null) {
			throw new ScraperExecutionException("Source must not be null");
		}
		if (context.keyword() == null) {
			throw new ScraperExecutionException("Keyword must not be null");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
	}

	private ScraperExecutionResult withTiming(ScraperExecutionResult result, OffsetDateTime startedAt) {
		ScraperExecutionResult safeResult = result == null ? ScraperExecutionResult.empty() : result;
		return safeResult.withTiming(startedAt, scraperClock.now());
	}
}
