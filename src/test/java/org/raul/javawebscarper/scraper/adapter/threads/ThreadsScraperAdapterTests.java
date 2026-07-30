package org.raul.javawebscarper.scraper.adapter.threads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.SourceType;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsScraperAdapterTests {

	private ThreadsProperties properties;
	private ThreadsScraperAdapter adapter;

	@BeforeEach
	void setUp() {
		properties = new ThreadsProperties();
		adapter = new ThreadsScraperAdapter(null, properties, new ThreadsDateParser());
	}

	@Test
	void registersForThreadsSource() {
		assertThat(adapter.sourceCode()).isEqualTo("THREADS");
		assertThat(adapter.supports(Source.builder()
				.code("THREADS")
				.baseUrl("https://www.threads.com/")
				.build())).isTrue();
	}

	@Test
	void reportsMissingAuthenticationState() {
		ScraperExecutionResult result = adapter.scrape(context("court"));

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).contains("THREADS_AUTH_STATE_MISSING");
		assertThat(result.errorMessage()).contains("threadsAuthStateInteractive");
	}

	@Test
	void rejectsBlankKeywordBeforeOpeningBrowser() {
		ScraperExecutionResult result = adapter.scrape(context(" "));

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).contains("THREADS_BAD_REQUEST");
	}

	private ScraperExecutionContext context(String word) {
		Source source = Source.builder()
				.id(1)
				.code("THREADS")
				.type(SourceType.SOCIAL)
				.baseUrl("https://www.threads.com/")
				.build();
		Keyword keyword = Keyword.builder().id(1).word(word).language(Language.AZ).build();
		ScrapeJob job = ScrapeJob.builder().id(UUID.randomUUID()).source(source).keyword(keyword).build();
		return new ScraperExecutionContext(
				job,
				source,
				keyword,
				OffsetDateTime.parse("2026-07-14T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-14T23:59:59+04:00"),
				5,
				100,
				Map.of()
		);
	}
}
