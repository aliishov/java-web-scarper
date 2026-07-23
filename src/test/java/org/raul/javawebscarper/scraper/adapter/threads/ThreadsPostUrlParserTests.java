package org.raul.javawebscarper.scraper.adapter.threads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsPostUrlParserTests {

	@Test
	void parsesAndCanonicalizesThreadsPostUrl() {
		ThreadsPostUrl parsed = ThreadsPostUrlParser.parse(
				"https://threads.net/@Example.User/post/AbC_123?xmt=AQG"
		).orElseThrow();

		assertThat(parsed.externalPostId()).isEqualTo("AbC_123");
		assertThat(parsed.username()).isEqualTo("Example.User");
		assertThat(parsed.canonicalUrl()).isEqualTo("https://www.threads.com/@Example.User/post/AbC_123");
	}

	@Test
	void rejectsProfilesAndForeignHosts() {
		assertThat(ThreadsPostUrlParser.parse("https://www.threads.com/@example")).isEmpty();
		assertThat(ThreadsPostUrlParser.parse("https://example.com/@user/post/AbC123")).isEmpty();
	}
}
