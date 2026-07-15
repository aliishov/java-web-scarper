package org.raul.javawebscarper.scraper.adapter.xcom;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

import static org.assertj.core.api.Assertions.assertThat;

class XComScraperSupportTests {

	@Test
	void supportsCurrentAndLegacySourceCodes() {
		assertThat(XComScraperSupport.supports(Source.builder().code("X_COM").build())).isTrue();
		assertThat(XComScraperSupport.supports(Source.builder().code("TWITTER").build())).isTrue();
		assertThat(XComScraperSupport.supports(Source.builder().code("TWITTER_X").build())).isTrue();
		assertThat(XComScraperSupport.supports(Source.builder().code("X").build())).isTrue();
	}

	@Test
	void supportsXAndTwitterBaseUrls() {
		assertThat(XComScraperSupport.supports(Source.builder().code("social").baseUrl("https://x.com/").build())).isTrue();
		assertThat(XComScraperSupport.supports(Source.builder().code("social").baseUrl("https://twitter.com/").build())).isTrue();
	}

	@Test
	void parsesAndCanonicalizesStatusUrls() {
		assertThat(XComScraperSupport.parseStatusUrl("https://twitter.com/realUser/status/123456789/video/1?ref=home"))
				.hasValue(new XStatusUrl("123456789", "https://x.com/realUser/status/123456789", "realUser"));
		assertThat(XComScraperSupport.parseStatusUrl("/realUser/status/123456789/photo/1"))
				.hasValue(new XStatusUrl("123456789", "https://x.com/realUser/status/123456789", "realUser"));
	}

	@Test
	void rejectsNonStatusUrls() {
		assertThat(XComScraperSupport.parseStatusUrl("https://example.com/realUser/status/123")).isEmpty();
		assertThat(XComScraperSupport.parseStatusUrl("https://x.com/realUser")).isEmpty();
	}

	@Test
	void filtersPostMediaButAllowsProfileAvatars() {
		assertThat(XComScraperSupport.isAllowedMediaUrl("blob:https://x.com/123")).isFalse();
		assertThat(XComScraperSupport.isAllowedMediaUrl("https://pbs.twimg.com/profile_images/avatar.jpg")).isFalse();
		assertThat(XComScraperSupport.isAllowedMediaUrl("https://abs.twimg.com/emoji/v2/72x72/1f600.png")).isFalse();
		assertThat(XComScraperSupport.isAllowedMediaUrl("https://pbs.twimg.com/media/photo.jpg")).isTrue();

		assertThat(XComScraperSupport.isAllowedAvatarUrl("https://pbs.twimg.com/profile_images/avatar.jpg")).isTrue();
		assertThat(XComScraperSupport.isAllowedAvatarUrl("blob:https://x.com/123")).isFalse();
	}

	@Test
	void normalizesKnownLanguagesWithFallback() {
		assertThat(XComScraperSupport.normalizeLanguage("az", "RU")).isEqualTo("az");
		assertThat(XComScraperSupport.normalizeLanguage(null, "RU")).isEqualTo("ru");
		assertThat(XComScraperSupport.normalizeLanguage("unknown", "TR")).isEqualTo("tr");
		assertThat(XComScraperSupport.normalizeLanguage("unknown", "unknown")).isNull();
	}
}
