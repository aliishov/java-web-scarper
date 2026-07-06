package org.raul.javawebscarper.scraper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.config.ScraperEngineProperties;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.UnsupportedSourceScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperEngine;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionException;
import org.raul.javawebscarper.scraper.registry.ScraperAdapterRegistry;
import org.raul.javawebscarper.scraper.support.ScraperClock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubScraperRunnerTests {

	private ScraperEngineProperties properties;
	private StubScraperRunner runner;
	private ScrapeJob job;

	@BeforeEach
	void setUp() {
		properties = new ScraperEngineProperties();
		ScraperClock scraperClock = new ScraperClock(Clock.fixed(
				Instant.parse("2026-07-01T08:30:00Z"),
				ZoneOffset.UTC
		));
		UnsupportedSourceScraperAdapter unsupportedAdapter = new UnsupportedSourceScraperAdapter();
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(List.of(unsupportedAdapter), unsupportedAdapter);
		ScraperEngine scraperEngine = new ScraperEngine(registry, scraperClock);
		runner = new StubScraperRunner(scraperEngine, properties, scraperClock);

		Source source = Source.builder().id(1).code("baku-ws").build();
		Keyword keyword = Keyword.builder().id(1).word("economy").build();
		job = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.dateFrom(LocalDate.of(2026, 6, 30))
				.dateTo(LocalDate.of(2026, 7, 1))
				.build();
	}

	@Test
	void throwsWhenSourceIsUnsupportedByDefault() {
		assertThatThrownBy(() -> runner.run(job))
				.isInstanceOf(ScraperExecutionException.class)
				.hasMessageContaining("No scraper adapter is registered");
	}

	@Test
	void returnsEmptyResultForUnsupportedSourceWhenFailureIsDisabled() {
		properties.setFailOnUnsupportedSource(false);

		ScraperResult result = runner.run(job);

		assertThat(result.postsFound()).isZero();
		assertThat(result.postsSaved()).isZero();
		assertThat(result.savedPostIds()).isEmpty();
	}
}
