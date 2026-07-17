package org.raul.javawebscarper.scraper.adapter.facebook;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

import static org.assertj.core.api.Assertions.assertThat;

class FacebookScraperSupportTests {

	@Test
	void supportsCanonicalAndLegacySources() {
		assertThat(FacebookScraperSupport.supports(Source.builder().code("FACEBOOK").build())).isTrue();
		assertThat(FacebookScraperSupport.supports(Source.builder().code("FB").build())).isTrue();
		assertThat(FacebookScraperSupport.supports(Source.builder().code("META_FACEBOOK").build())).isTrue();
		assertThat(FacebookScraperSupport.supports(Source.builder().code("social").baseUrl("https://m.facebook.com/").build())).isTrue();
		assertThat(FacebookScraperSupport.supports(Source.builder().code("X_COM").baseUrl("https://x.com/").build())).isFalse();
	}

	@Test
	void normalizesSupportedLanguages() {
		assertThat(FacebookScraperSupport.normalizeLanguage("az_AZ", "ru")).isEqualTo("az");
		assertThat(FacebookScraperSupport.normalizeLanguage("ru-RU", "az")).isEqualTo("ru");
		assertThat(FacebookScraperSupport.normalizeLanguage(null, "EN")).isEqualTo("en");
		assertThat(FacebookScraperSupport.normalizeLanguage("de", "tr")).isEqualTo("tr");
	}

	@Test
	void detectsSponsoredAndExpandLabels() {
		assertThat(FacebookScraperSupport.isSponsoredText("Sponsored")).isTrue();
		assertThat(FacebookScraperSupport.isSponsoredText("Sponsorlu")).isTrue();
		assertThat(FacebookScraperSupport.isExpandLabel("See more")).isTrue();
		assertThat(FacebookScraperSupport.isExpandLabel("Daha çox")).isTrue();
		assertThat(FacebookScraperSupport.isExpandLabel("Share")).isFalse();
	}

	@Test
	void filtersMediaUrls() {
		assertThat(FacebookScraperSupport.isAllowedMediaUrl("https://scontent.xx.fbcdn.net/photo.jpg")).isTrue();
		assertThat(FacebookScraperSupport.isAllowedMediaUrl("blob:https://www.facebook.com/video")).isFalse();
		assertThat(FacebookScraperSupport.isAllowedMediaUrl("data:image/png;base64,abc")).isFalse();
		assertThat(FacebookScraperSupport.isAllowedMediaUrl("https://static.xx.fbcdn.net/rsrc.php/icon.png")).isFalse();
	}

	@Test
	void buildsDirectSearchUrls() {
		FacebookSearchQueryBuilder builder = new FacebookSearchQueryBuilder();

		assertThat(builder.buildSearchUrl("https://www.facebook.com/", "Məhkəmə", FacebookSearchMode.TOP))
				.isEqualTo("https://www.facebook.com/search/posts/?q=M%C9%99hk%C9%99m%C9%99");
		assertThat(builder.buildSearchUrl("https://www.facebook.com", "court", FacebookSearchMode.RECENT))
				.startsWith("https://www.facebook.com/search/posts/?q=court&filters=");
	}
}
