package org.raul.javawebscarper.scraper.adapter.threads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsSearchQueryBuilderTests {

	private final ThreadsSearchQueryBuilder builder = new ThreadsSearchQueryBuilder();

	@Test
	void buildsEncodedKeywordSearchUrl() {
		assertThat(builder.buildSearchUrl("https://www.threads.com/", "Bakı xəbərləri"))
				.isEqualTo("https://www.threads.com/search?q=Bakı%20xəbərləri&serp_type=default"
						.replace("ı", "%C4%B1")
						.replace("ə", "%C9%99"));
	}
}
