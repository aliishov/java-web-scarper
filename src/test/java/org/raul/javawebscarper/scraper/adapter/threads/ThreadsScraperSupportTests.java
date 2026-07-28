package org.raul.javawebscarper.scraper.adapter.threads;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

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
}
