package org.raul.javawebscarper.scraper.adapter.threads;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsScraperSupportTests {

	@Test
	void supportsCanonicalAndLegacyThreadsSources() {
		Source canonical = new Source();
		canonical.setCode("THREADS");
		canonical.setBaseUrl("https://www.threads.com/");
		Source legacy = new Source();
		legacy.setCode("META_THREADS");

		assertThat(ThreadsScraperSupport.supports(canonical)).isTrue();
		assertThat(ThreadsScraperSupport.supports(legacy)).isTrue();
	}

	@Test
	void discoversCanonicalCandidatesAndDeduplicatesTrackingVariants() {
		String html = """
				<main>
				  <article>
				    <a href="/@news/post/AbC123?xmt=one">first</a>
				    <a href="https://www.threads.net/@news/post/AbC123#reply">duplicate</a>
				  </article>
				  <article><a href="/@other/post/XyZ789">second</a></article>
				</main>
				""";
		ThreadsScrapeDiagnostics diagnostics = new ThreadsScrapeDiagnostics();

		var candidates = ThreadsScraperSupport.discoverCandidates(
				Jsoup.parse(html, ThreadsScraperSupport.BASE_URL),
				10,
				diagnostics
		);

		assertThat(candidates).extracting(ThreadsPostCandidate::externalPostId)
				.containsExactly("AbC123", "XyZ789");
		assertThat(diagnostics.duplicateCandidates).isEqualTo(1);
	}

	@Test
	void skipsExtractedPostWhenTextDoesNotMatchKeyword() {
		String html = """
				<main>
				  <article>
				    <a href="/@news">News</a>
				    <a href="/@news/post/AbC123">post</a>
				    <time datetime="2026-07-07T11:45:00+04:00"></time>
				    <div dir="auto">Completely unrelated social text.</div>
				  </article>
				</main>
				""";
		ThreadsScrapeDiagnostics diagnostics = new ThreadsScrapeDiagnostics();

		var post = ThreadsScraperSupport.extractPost(
				Jsoup.parse(html, ThreadsScraperSupport.BASE_URL),
				context("Məhkəmə"),
				new ThreadsPostCandidate("AbC123", "news", ThreadsScraperSupport.BASE_URL + "/@news/post/AbC123"),
				new ThreadsDateParser(),
				new ThreadsProperties(),
				diagnostics
		);

		assertThat(post).isEmpty();
		assertThat(diagnostics.keywordMismatchSkipped).isEqualTo(1);
	}

	@Test
	void extractsPostWhenTextMatchesKeywordAfterNormalization() {
		String html = """
				<main>
				  <article>
				    <a href="/@news">News</a>
				    <a href="/@news/post/AbC123">post</a>
				    <time datetime="2026-07-07T11:45:00+04:00"></time>
				    <div dir="auto">Ali Mehkeme yeni qərar qəbul edib.</div>
				  </article>
				</main>
				""";
		ThreadsScrapeDiagnostics diagnostics = new ThreadsScrapeDiagnostics();

		var post = ThreadsScraperSupport.extractPost(
				Jsoup.parse(html, ThreadsScraperSupport.BASE_URL),
				context("Məhkəmə"),
				new ThreadsPostCandidate("AbC123", "news", ThreadsScraperSupport.BASE_URL + "/@news/post/AbC123"),
				new ThreadsDateParser(),
				new ThreadsProperties(),
				diagnostics
		);

		assertThat(post).isPresent();
		assertThat(post.get().text()).isEqualTo("Ali Mehkeme yeni qərar qəbul edib.");
		assertThat(diagnostics.keywordMismatchSkipped).isZero();
	}

	private ScraperExecutionContext context(String keywordWord) {
		Source source = Source.builder()
				.id(1)
				.code("THREADS")
				.name("Threads")
				.baseUrl(ThreadsScraperSupport.BASE_URL)
				.build();
		Keyword keyword = Keyword.builder()
				.id(7)
				.word(keywordWord)
				.build();
		ScrapeJob job = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.dateFrom(LocalDate.of(2026, 7, 7))
				.dateTo(LocalDate.of(2026, 7, 7))
				.build();
		return new ScraperExecutionContext(
				job,
				source,
				keyword,
				OffsetDateTime.parse("2026-07-07T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-07T23:59:59+04:00"),
				5,
				100,
				Map.of()
		);
	}
}
