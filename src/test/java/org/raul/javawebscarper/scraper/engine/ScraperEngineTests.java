package org.raul.javawebscarper.scraper.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.ScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.UnsupportedSourceScraperAdapter;
import org.raul.javawebscarper.scraper.registry.ScraperAdapterRegistry;
import org.raul.javawebscarper.scraper.support.ScraperClock;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperEngineTests {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-01T08:30:00Z"), ZoneOffset.UTC);

	private Source source;
	private Keyword keyword;
	private ScrapeJob job;

	@BeforeEach
	void setUp() {
		source = Source.builder().id(1).code("baku-ws").build();
		keyword = Keyword.builder().id(1).word("economy").build();
		job = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.build();
	}

	@Test
	void returnsFailedResultWhenAdapterThrowsException() {
		ScraperAdapter throwingAdapter = new ThrowingScraperAdapter("baku-ws");
		ScraperEngine engine = engineWithAdapters(List.of(throwingAdapter));

		ScraperExecutionResult result = engine.execute(context());

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).isEqualTo("Unexpected scraper engine error: adapter exploded");
		assertThat(result.startedAt()).isEqualTo(OffsetDateTime.parse("2026-07-01T08:30:00Z"));
		assertThat(result.finishedAt()).isEqualTo(OffsetDateTime.parse("2026-07-01T08:30:00Z"));
	}

	@Test
	void returnsUnsupportedResultWhenAdapterMissing() {
		ScraperEngine engine = engineWithAdapters(List.of());

		ScraperExecutionResult result = engine.execute(context());

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.UNSUPPORTED);
		assertThat(result.errorMessage()).contains("baku-ws");
	}

	private ScraperEngine engineWithAdapters(List<ScraperAdapter> adapters) {
		UnsupportedSourceScraperAdapter unsupportedAdapter = new UnsupportedSourceScraperAdapter();
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(
				concat(adapters, unsupportedAdapter),
				unsupportedAdapter
		);
		return new ScraperEngine(registry, new ScraperClock(FIXED_CLOCK));
	}

	private List<ScraperAdapter> concat(List<ScraperAdapter> adapters, ScraperAdapter adapter) {
		return java.util.stream.Stream.concat(adapters.stream(), java.util.stream.Stream.of(adapter)).toList();
	}

	private ScraperExecutionContext context() {
		return new ScraperExecutionContext(
				job,
				source,
				keyword,
				OffsetDateTime.parse("2026-06-30T00:00:00Z"),
				OffsetDateTime.parse("2026-07-01T23:59:59Z"),
				5,
				100,
				Map.of()
		);
	}

	private record ThrowingScraperAdapter(String sourceCode) implements ScraperAdapter {

		@Override
		public boolean supports(Source source) {
			return source != null && sourceCode.equals(source.getCode());
		}

		@Override
		public ScraperExecutionResult scrape(ScraperExecutionContext context) {
			throw new IllegalStateException("adapter exploded");
		}
	}
}
