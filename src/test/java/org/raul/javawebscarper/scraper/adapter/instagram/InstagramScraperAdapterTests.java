package org.raul.javawebscarper.scraper.adapter.instagram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.browser.BrowserPage;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.SourceType;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionStatus;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstagramScraperAdapterTests {

	private InstagramProperties properties;
	private InstagramScraperAdapter adapter;

	@BeforeEach
	void setUp() {
		properties = new InstagramProperties();
		adapter = new InstagramScraperAdapter(
				null,
				properties,
				new InstagramSearchQueryBuilder(),
				new InstagramDateParser(),
				new InstagramAuthenticationVerifier()
		);
	}

	@Test
	void supportsInstagramSourceAndLegacyAliases() {
		assertThat(adapter.sourceCode()).isEqualTo("INSTAGRAM");
		assertThat(adapter.supports(Source.builder().code("INSTAGRAM").baseUrl("https://www.instagram.com/").build())).isTrue();
		assertThat(adapter.supports(Source.builder().code("IG").baseUrl("https://instagram.com/").build())).isTrue();
		assertThat(adapter.supports(Source.builder().code("META_INSTAGRAM").build())).isTrue();
	}

	@Test
	void failsWithClearErrorWhenAuthStateIsMissing() {
		ScraperExecutionResult result = adapter.scrape(context(Language.AZ));

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).contains("INSTAGRAM_AUTH_STATE_MISSING");
	}

	@Test
	void failsWithClearErrorWhenConfiguredAuthStateFileDoesNotExist() {
		properties.setAuthStatePath("build/tmp/nonexistent-instagram-storage-state.json");

		ScraperExecutionResult result = adapter.scrape(context(Language.AZ));

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).contains("INSTAGRAM_AUTH_STATE_MISSING");
		assertThat(result.errorMessage()).contains("authStateUsed=false");
	}

	@Test
	void reloadsAndScrollsSearchPageWhenSearchResultLinkIsNotInitiallyClickable() throws Exception {
		properties.setMaxScrollAttempts(1);
		BrowserPage searchPage = mock(BrowserPage.class);
		String searchUrl = "https://www.instagram.com/explore/search/keyword/?q=porsche";
		InstagramPostCandidate candidate = new InstagramPostCandidate(
				"DUgdGwECSMZ",
				"https://www.instagram.com/p/DUgdGwECSMZ/",
				InstagramPostType.POST,
				null
		);
		when(searchPage.url()).thenReturn(searchUrl);
		when(searchPage.clickLinkByHref(anyString())).thenReturn(false, false, true);

		Method method = InstagramScraperAdapter.class.getDeclaredMethod(
				"openCandidateFromSearchPage",
				BrowserPage.class,
				InstagramPostCandidate.class,
				String.class
		);
		method.setAccessible(true);
		method.invoke(adapter, searchPage, candidate, searchUrl);

		verify(searchPage).navigate(searchUrl);
		verify(searchPage, never()).navigate(candidate.postUrl(), searchUrl);
		verify(searchPage).scrollBy(1_100, properties.getScrollDelayMs());
		verify(searchPage).waitForSelector(InstagramSelectors.BODY, properties.getTimelineLoadTimeoutMs());
		verify(searchPage, times(2)).waitForTimeout(properties.getActionDelayMs());
	}

	private ScraperExecutionContext context(Language language) {
		Source source = Source.builder()
				.id(1)
				.code("INSTAGRAM")
				.type(SourceType.SOCIAL)
				.baseUrl("https://www.instagram.com/")
				.build();
		Keyword keyword = Keyword.builder()
				.id(1)
				.word("court")
				.language(language)
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
				OffsetDateTime.parse("2026-07-14T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-14T23:59:59+04:00"),
				5,
				100,
				Map.of()
		);
	}
}
