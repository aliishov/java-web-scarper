package org.raul.javawebscarper.scraper.adapter.instagram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstagramSearchQueryBuilderTests {

	private final InstagramSearchQueryBuilder builder = new InstagramSearchQueryBuilder();

	@Test
	void allowsSimpleKeywordForHashtagFallback() {
		assertThat(builder.hashtagFallbackAllowed("azerbaijan")).isTrue();
		assertThat(builder.buildSearchUrl("https://www.instagram.com", "azerbaijan", InstagramSearchMode.HASHTAG))
				.isEqualTo("https://www.instagram.com/explore/tags/azerbaijan/");
	}

	@Test
	void rejectsMultiWordKeywordForHashtagFallback() {
		assertThat(builder.hashtagFallbackAllowed("azerbaijan news")).isFalse();
	}

	@Test
	void addsAzerbaijanContextToKeywordSearch() {
		assertThat(builder.buildSearchUrl("https://www.instagram.com", "Ali Mehkeme", InstagramSearchMode.KEYWORD))
				.isEqualTo("https://www.instagram.com/explore/search/keyword/?q=Ali%20Mehkeme%20Az%C9%99rbaycan");
	}
}
