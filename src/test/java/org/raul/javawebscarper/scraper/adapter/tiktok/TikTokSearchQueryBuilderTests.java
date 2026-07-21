package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TikTokSearchQueryBuilderTests {

	private final TikTokSearchQueryBuilder queryBuilder = new TikTokSearchQueryBuilder();

	@Test
	void buildsKeywordSearchUrlWithUnicodeEncoding() {
		String url = queryBuilder.buildSearchUrl("https://www.tiktok.com/", "İlham Əliyev", TikTokSearchMode.KEYWORD);

		assertThat(url).isEqualTo("https://www.tiktok.com/search/video?q=%C4%B0lham%20%C6%8Fliyev");
	}

	@Test
	void buildsHashtagUrlOnlyForExplicitHashtagModeOrAutoHashtag() {
		assertThat(queryBuilder.buildSearchUrl("https://www.tiktok.com", "#Azərbaycan", TikTokSearchMode.AUTO))
				.isEqualTo("https://www.tiktok.com/tag/Az%C9%99rbaycan");
		assertThat(queryBuilder.buildSearchUrl("https://www.tiktok.com", "İlham Əliyev", TikTokSearchMode.AUTO))
				.contains("/search/video?q=");
	}

	@Test
	void resolvesSearchModeWithoutTurningWordsIntoHashtags() {
		assertThat(queryBuilder.resolveMode("#Bakı", TikTokSearchMode.AUTO)).isEqualTo(TikTokSearchMode.HASHTAG);
		assertThat(queryBuilder.resolveMode("Bakı", TikTokSearchMode.AUTO)).isEqualTo(TikTokSearchMode.KEYWORD);
		assertThat(queryBuilder.resolveMode("Bakı", TikTokSearchMode.HASHTAG)).isEqualTo(TikTokSearchMode.HASHTAG);
	}
}
