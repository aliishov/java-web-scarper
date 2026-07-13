package org.raul.javawebscarper.scraper.adapter.caliberaz;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

import static org.assertj.core.api.Assertions.assertThat;

class CaliberAzScraperSupportTests {

	@Test
	void supportsCaliberAzSourceCode() {
		Source source = Source.builder()
				.code("CALIBER_AZ")
				.baseUrl("https://example.com")
				.build();

		assertThat(CaliberAzScraperSupport.supports(source)).isTrue();
	}

	@Test
	void supportsCaliberAzBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://caliber.az/")
				.build();

		assertThat(CaliberAzScraperSupport.supports(source)).isTrue();
	}

	@Test
	void extractsSlugExternalPostId() {
		assertThat(CaliberAzScraperSupport.extractExternalPostId(
				"https://caliber.az/post/prokuratura-trebuet-smertnoj-kazni-dlya-eks-prezidenta-yuzhnoj-korei"
		)).contains("prokuratura-trebuet-smertnoj-kazni-dlya-eks-prezidenta-yuzhnoj-korei");
	}

	@Test
	void acceptsOnlyPostUrls() {
		assertThat(CaliberAzScraperSupport.normalizePostUrl("https://caliber.az/post/example")).contains("https://caliber.az/post/example");
		assertThat(CaliberAzScraperSupport.normalizePostUrl("/post/example#comments")).contains("https://caliber.az/post/example");
		assertThat(CaliberAzScraperSupport.normalizePostUrl("https://caliber.az/category/mir")).isEmpty();
		assertThat(CaliberAzScraperSupport.normalizePostUrl("https://caliber.az/search/test")).isEmpty();
		assertThat(CaliberAzScraperSupport.normalizePostUrl("https://example.com/post/example")).isEmpty();
	}

	@Test
	void extractsBackgroundImageUrl() {
		assertThat(CaliberAzScraperSupport.extractBackgroundImageUrl("background-image:url(https://example.com/a.webp)"))
				.contains("https://example.com/a.webp");
		assertThat(CaliberAzScraperSupport.extractBackgroundImageUrl("background-image: url('https://example.com/a.webp')"))
				.contains("https://example.com/a.webp");
		assertThat(CaliberAzScraperSupport.extractBackgroundImageUrl("background-image: url(\"https://example.com/a.webp\")"))
				.contains("https://example.com/a.webp");
	}

	@Test
	void returnsEmptyForInvalidBackgroundImage() {
		assertThat(CaliberAzScraperSupport.extractBackgroundImageUrl("background: none")).isEmpty();
		assertThat(CaliberAzScraperSupport.extractBackgroundImageUrl(null)).isEmpty();
	}
}
