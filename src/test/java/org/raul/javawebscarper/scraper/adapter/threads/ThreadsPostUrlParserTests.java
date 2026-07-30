package org.raul.javawebscarper.scraper.adapter.threads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsPostUrlParserTests {

	@Test
	void parsesCurrentAndLegacyThreadsUrls() {
		assertThat(ThreadsPostUrlParser.parse("https://www.threads.com/@news.az/post/ABC_123?x=1"))
				.contains(new ThreadsPostUrl(
						"news.az",
						"ABC_123",
						"https://www.threads.com/@news.az/post/ABC_123"
				));
		assertThat(ThreadsPostUrlParser.parse("https://www.threads.net/@reporter/post/XYZ-9"))
				.contains(new ThreadsPostUrl(
						"reporter",
						"XYZ-9",
						"https://www.threads.com/@reporter/post/XYZ-9"
				));
	}

	@Test
	void rejectsProfilesAndForeignHosts() {
		assertThat(ThreadsPostUrlParser.parse("https://www.threads.com/@news.az")).isEmpty();
		assertThat(ThreadsPostUrlParser.parse("https://example.com/@news.az/post/ABC123")).isEmpty();
	}
}
