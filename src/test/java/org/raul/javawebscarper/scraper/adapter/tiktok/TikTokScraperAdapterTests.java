package org.raul.javawebscarper.scraper.adapter.tiktok;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TikTokScraperAdapterTests {

	@Test
	void authFailureDoesNotBecomeEmptyResult() {
		TikTokSessionResolver resolver = mock(TikTokSessionResolver.class);
		when(resolver.resolve(any())).thenThrow(new TikTokAuthenticationException(
				TikTokAuthenticationStatus.CAPTCHA_REQUIRED,
				"TIKTOK_CAPTCHA_REQUIRED: TikTok requires CAPTCHA."
		));
		TikTokScraperAdapter adapter = new TikTokScraperAdapter(
				resolver,
				new TikTokProperties(),
				new TikTokSearchQueryBuilder(),
				new TikTokDateParser(),
				new TikTokAuthenticationVerifier(),
				new TikTokPageReadinessVerifier()
		);

		ScraperExecutionResult result = adapter.scrape(context());

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).contains("TIKTOK_CAPTCHA_REQUIRED");
		assertThat(result.posts()).isEmpty();
	}

	@Test
	void disabledAdapterDoesNotTouchSessionResolver() {
		TikTokProperties properties = new TikTokProperties();
		properties.setEnabled(false);
		TikTokSessionResolver resolver = mock(TikTokSessionResolver.class);
		TikTokScraperAdapter adapter = new TikTokScraperAdapter(
				resolver,
				properties,
				new TikTokSearchQueryBuilder(),
				new TikTokDateParser(),
				new TikTokAuthenticationVerifier(),
				new TikTokPageReadinessVerifier()
		);

		ScraperExecutionResult result = adapter.scrape(context());

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).contains("TIKTOK_SCRAPER_DISABLED");
	}

	private ScraperExecutionContext context() {
		Source source = Source.builder()
				.id(1)
				.code("TIKTOK")
				.type(SourceType.SOCIAL)
				.baseUrl("https://www.tiktok.com/")
				.build();
		Keyword keyword = Keyword.builder()
				.id(1)
				.word("Məhkəmə")
				.language(Language.AZ)
				.build();
		ScrapeJob job = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.build();
		return new ScraperExecutionContext(
				job,
				source,
				keyword,
				OffsetDateTime.parse("2026-07-15T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-15T23:59:59+04:00"),
				5,
				100,
				Map.of()
		);
	}
}
