package org.raul.javawebscarper.scraper.adapter.threads;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.model.enumerated.SourceType;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsScraperSupportTests {

	@Test
	void supportsThreadsSourceAliasesAndDomains() {
		assertThat(ThreadsScraperSupport.supports(source("THREADS", "https://www.threads.com/"))).isTrue();
		assertThat(ThreadsScraperSupport.supports(source("META_THREADS", null))).isTrue();
		assertThat(ThreadsScraperSupport.supports(source("OTHER", "https://www.threads.net/"))).isTrue();
		assertThat(ThreadsScraperSupport.supports(source("X_COM", "https://x.com"))).isFalse();
	}

	@Test
	void discoversUniquePostCandidates() {
		Document document = Jsoup.parse("""
				<a href="/@news.az/post/ABC123">first</a>
				<a href="https://www.threads.net/@news.az/post/ABC123">duplicate</a>
				<a href="/@reporter/post/XYZ999">second</a>
				""", ThreadsScraperSupport.BASE_URL);

		List<ThreadsPostCandidate> candidates = ThreadsScraperSupport.discoverCandidates(document, 10);

		assertThat(candidates).containsExactly(
				new ThreadsPostCandidate("news.az", "ABC123", "https://www.threads.com/@news.az/post/ABC123"),
				new ThreadsPostCandidate("reporter", "XYZ999", "https://www.threads.com/@reporter/post/XYZ999")
		);
	}

	@Test
	void extractsPostAuthorDateTextAndMedia() {
		Document document = Jsoup.parse("""
				<html lang="az"><body>
				<article>
				  <a href="/@news.az"><img alt="news.az profile picture" src="https://cdn.test/avatar.jpg">News AZ</a>
				  <time datetime="2026-07-14T12:30:00+04:00">2h</time>
				  <div data-pressable-container="true"><div dir="auto">Vacib Threads xəbəri</div></div>
				  <img alt="news photo" src="https://cdn.test/news.jpg">
				  <video src="https://cdn.test/video.mp4"></video>
				</article>
				</body></html>
				""", ThreadsScraperSupport.BASE_URL);
		ThreadsPostCandidate candidate = new ThreadsPostCandidate(
				"news.az",
				"ABC123",
				"https://www.threads.com/@news.az/post/ABC123"
		);

		Optional<ScrapedPostDTO> result = ThreadsScraperSupport.extractPost(
				document,
				context(),
				candidate,
				new ThreadsDateParser()
		);

		assertThat(result).isPresent();
		ScrapedPostDTO post = result.orElseThrow();
		assertThat(post.externalPostId()).isEqualTo("ABC123");
		assertThat(post.author().username()).isEqualTo("news.az");
		assertThat(post.author().displayName()).isEqualTo("News AZ");
		assertThat(post.text()).isEqualTo("Vacib Threads xəbəri");
		assertThat(post.language()).isEqualTo("az");
		assertThat(post.media()).extracting(media -> media.mediaType())
				.containsExactly(MediaType.IMAGE, MediaType.VIDEO);
	}

	private Source source(String code, String baseUrl) {
		return Source.builder().code(code).baseUrl(baseUrl).type(SourceType.SOCIAL).build();
	}

	private ScraperExecutionContext context() {
		Source source = source("THREADS", "https://www.threads.com/");
		Keyword keyword = Keyword.builder().id(1).word("xəbər").language(Language.AZ).build();
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
