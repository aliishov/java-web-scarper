package org.raul.javawebscarper.scraper.adapter.threads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsPageInspectorTests {

	@Test
	void recognizesReadySearchPage() {
		assertThat(ThreadsPageInspector.inspect(
				"https://www.threads.com/search?q=java",
				"<main><article><a href='/@dev/post/AbC123'>post</a></article></main>"
		)).isEqualTo(ThreadsPageStatus.READY);
	}

	@Test
	void recognizesBlockers() {
		assertThat(ThreadsPageInspector.inspect(
				"https://www.threads.com/login",
				"<form><input name='username'><input name='password'></form>"
		)).isEqualTo(ThreadsPageStatus.LOGIN_REQUIRED);
		assertThat(ThreadsPageInspector.inspect(
				"https://www.threads.com/",
				"<main>Try again later</main>"
		)).isEqualTo(ThreadsPageStatus.RATE_LIMITED);
	}
}
