package org.raul.javawebscarper.scraper.adapter.facebook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FacebookPostUrlParserTests {

	@Test
	void parsesPagePostUrlAndRemovesTrackingParams() {
		FacebookPostUrl parsed = FacebookPostUrlParser.parse(
				"https://www.facebook.com/example/posts/123456789?ref=search&__cft__[0]=secret"
		).orElseThrow();

		assertThat(parsed.externalPostId()).isEqualTo("123456789");
		assertThat(parsed.canonicalUrl()).isEqualTo("https://www.facebook.com/example/posts/123456789");
		assertThat(parsed.authorExternalId()).isEqualTo("example");
		assertThat(parsed.type()).isEqualTo(FacebookPostType.POST);
	}

	@Test
	void parsesStoryAndPermalinkUrls() {
		assertThat(FacebookPostUrlParser.parse("https://facebook.com/story.php?story_fbid=111&id=222&ref=search"))
				.get()
				.extracting(FacebookPostUrl::externalPostId, FacebookPostUrl::canonicalUrl, FacebookPostUrl::authorExternalId)
				.containsExactly("111", "https://www.facebook.com/story.php?story_fbid=111&id=222", "222");

		assertThat(FacebookPostUrlParser.parse("https://www.facebook.com/permalink.php?story_fbid=333&id=444&mibextid=abc"))
				.get()
				.extracting(FacebookPostUrl::externalPostId, FacebookPostUrl::canonicalUrl, FacebookPostUrl::authorExternalId)
				.containsExactly("333", "https://www.facebook.com/permalink.php?story_fbid=333&id=444", "444");
	}

	@Test
	void parsesModernSharePostUrls() {
		assertThat(FacebookPostUrlParser.parse("https://www.facebook.com/share/p/1AbCdEfgHi/?mibextid=test").orElseThrow())
				.extracting(FacebookPostUrl::externalPostId, FacebookPostUrl::canonicalUrl, FacebookPostUrl::type)
				.containsExactly("1AbCdEfgHi", "https://www.facebook.com/share/p/1AbCdEfgHi", FacebookPostType.POST);
	}

	@Test
	void parsesGroupPhotoWatchAndReelUrls() {
		assertThat(FacebookPostUrlParser.parse("/groups/my-group/posts/555").orElseThrow().canonicalUrl())
				.isEqualTo("https://www.facebook.com/groups/my-group/posts/555");
		assertThat(FacebookPostUrlParser.parse("https://m.facebook.com/photo/?fbid=666&id=777&locale=ru_RU").orElseThrow().canonicalUrl())
				.isEqualTo("https://www.facebook.com/photo?fbid=666&id=777");
		assertThat(FacebookPostUrlParser.parse("https://www.facebook.com/watch/?v=888&ref=watch_permalink").orElseThrow().canonicalUrl())
				.isEqualTo("https://www.facebook.com/watch?v=888");
		assertThat(FacebookPostUrlParser.parse("https://www.facebook.com/reel/999?mibextid=abc").orElseThrow())
				.extracting(FacebookPostUrl::externalPostId, FacebookPostUrl::type, FacebookPostUrl::isReel)
				.containsExactly("999", FacebookPostType.REEL, true);
	}

	@Test
	void rejectsSearchNavigationAndExternalHosts() {
		assertThat(FacebookPostUrlParser.parse("https://www.facebook.com/search/videos")).isEmpty();
		assertThat(FacebookPostUrlParser.parse("https://example.com/example/posts/123")).isEmpty();
		assertThat(FacebookPostUrlParser.parse("not a url")).isEmpty();
	}

	@Test
	void fallbackExternalPostIdIsStable() {
		assertThat(FacebookPostUrlParser.fallbackExternalPostId("https://www.facebook.com/example/posts/123"))
				.isEqualTo(FacebookPostUrlParser.fallbackExternalPostId("https://www.facebook.com/example/posts/123"))
				.hasSize(32);
	}
}
