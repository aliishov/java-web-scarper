package org.raul.javawebscarper.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.config.ScraperEngineProperties;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.enumerated.SearchRegion;
import org.raul.javawebscarper.scraper.engine.ScraperEngine;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionException;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionStatus;
import org.raul.javawebscarper.scraper.support.ScraperClock;
import org.raul.javawebscarper.service.ScrapedPostIngestionResult;
import org.raul.javawebscarper.service.ScrapedPostIngestionService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EngineScraperRunner implements ScraperRunner {

	private final ScraperEngine scraperEngine;
	private final ScraperEngineProperties properties;
	private final ScraperClock scraperClock;
	private final ScrapedPostIngestionService ingestionService;

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

		ScraperExecutionContext context = toExecutionContext(job);
		ScraperExecutionResult result = scraperEngine.execute(context);
		return toScraperResult(context, result);
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
				resolveSearchRegion(job),
				Map.of(
						"runner", getClass().getSimpleName(),
						"searchRegion", resolveSearchRegion(job).name()
				)
		);
	}

	private SearchRegion resolveSearchRegion(ScrapeJob job) {
		return job.getSearchRegion() == null ? SearchRegion.defaultRegion() : job.getSearchRegion();
	}

	private ScraperResult toScraperResult(ScraperExecutionContext context, ScraperExecutionResult result) {
		if (result.status() == ScraperExecutionStatus.FAILED) {
			throw new ScraperExecutionException(result.errorMessage());
		}
		if (result.status() == ScraperExecutionStatus.UNSUPPORTED && properties.isFailOnUnsupportedSource()) {
			throw new ScraperExecutionException(result.errorMessage());
		}
		if (result.status() == ScraperExecutionStatus.EMPTY || result.status() == ScraperExecutionStatus.UNSUPPORTED) {
			return ScraperResult.empty();
		}
		if (result.posts().isEmpty()) {
			return new ScraperResult(result.postsFound(), 0, List.of());
		}

		ScrapedPostIngestionResult ingestionResult = ingestionService.ingest(context, result);
		log.info(
				"Scraper ingestion result: jobId={}, postsReceived={}, postsCreated={}, postsUpdated={}, "
						+ "postsSkipped={}, mediaCreated={}, keywordsLinked={}, errors={}",
				context.job().getId(),
				ingestionResult.postsReceived(),
				ingestionResult.postsCreated(),
				ingestionResult.postsUpdated(),
				ingestionResult.postsSkipped(),
				ingestionResult.mediaCreated(),
				ingestionResult.keywordsLinked(),
				ingestionResult.errors().size()
		);
		if (ingestionResult.allPostsFailed()) {
			throw new ScraperExecutionException("Scraped post ingestion failed for all posts: "
					+ String.join("; ", ingestionResult.errors()));
		}
		return new ScraperResult(
				result.postsFound(),
				ingestionResult.postsSaved(),
				ingestionResult.savedPostIds()
		);
	}
}
