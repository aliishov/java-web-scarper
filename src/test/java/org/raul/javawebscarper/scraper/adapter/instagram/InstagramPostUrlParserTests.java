package org.raul.javawebscarper.scraper.adapter.instagram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstagramPostUrlParserTests {

	@Test
	void parsesPostUrlsAndRemovesTrackingData() {
		InstagramPostUrl parsed = InstagramPostUrlParser.parse(
				"https://www.instagram.com/p/ABC_123-def/?igsh=secret&utm_source=copy#comments"
		).orElseThrow();

		assertThat(parsed.externalPostId()).isEqualTo("ABC_123-def");
		assertThat(parsed.canonicalUrl()).isEqualTo("https://www.instagram.com/p/ABC_123-def/");
		assertThat(parsed.type()).isEqualTo(InstagramPostType.POST);
	}

	@Test
	void parsesRelativeReelAndTvUrls() {
		assertThat(InstagramPostUrlParser.parse("/reel/REEL123/?igsh=abc").orElseThrow())
				.extracting(InstagramPostUrl::externalPostId, InstagramPostUrl::canonicalUrl, InstagramPostUrl::type)
				.containsExactly("REEL123", "https://www.instagram.com/reel/REEL123/", InstagramPostType.REEL);
		assertThat(InstagramPostUrlParser.parse("https://instagram.com/tv/TV123/").orElseThrow().canonicalUrl())
				.isEqualTo("https://www.instagram.com/tv/TV123/");
	}

	@Test
	void rejectsInvalidUrlsAndNonPostRoutes() {
		assertThat(InstagramPostUrlParser.parse("https://example.com/p/ABC123/")).isEmpty();
		assertThat(InstagramPostUrlParser.parse("https://www.instagram.com/explore/tags/baku/")).isEmpty();
		assertThat(InstagramPostUrlParser.parse("not a url")).isEmpty();
	}
}
