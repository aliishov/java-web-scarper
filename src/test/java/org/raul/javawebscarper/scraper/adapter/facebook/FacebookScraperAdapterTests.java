package org.raul.javawebscarper.scraper.adapter.facebook;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.model.enumerated.SourceType;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FacebookScraperAdapterTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private FacebookProperties properties;
	private FacebookScraperAdapter adapter;

	@BeforeEach
	void setUp() {
		properties = new FacebookProperties();
		adapter = new FacebookScraperAdapter(
				null,
				properties,
				new FacebookSearchQueryBuilder(),
				new FacebookDateParser(BAKU, Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), BAKU)),
				new FacebookAuthenticationVerifier()
		);
	}

	@Test
	void supportsFacebookSourceAndLegacyAliases() {
		assertThat(adapter.sourceCode()).isEqualTo("FACEBOOK");
		assertThat(adapter.supports(Source.builder().code("FACEBOOK").baseUrl("https://www.facebook.com/").build())).isTrue();
		assertThat(adapter.supports(Source.builder().code("FB").baseUrl("https://facebook.com/").build())).isTrue();
		assertThat(adapter.supports(Source.builder().code("META_FACEBOOK").baseUrl("https://m.facebook.com/").build())).isTrue();
	}

	@Test
	void failsWithClearErrorWhenAuthStateIsMissing() {
		ScraperExecutionResult result = adapter.scrape(context(Language.AZ));

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).contains("FACEBOOK_AUTH_STATE_MISSING");
	}

	@Test
	void failsWithClearErrorWhenConfiguredAuthStateFileDoesNotExist() {
		properties.setAuthStatePath("build/tmp/nonexistent-facebook-storage-state.json");

		ScraperExecutionResult result = adapter.scrape(context(Language.AZ));

		assertThat(result.status()).isEqualTo(ScraperExecutionStatus.FAILED);
		assertThat(result.errorMessage()).contains("FACEBOOK_AUTH_STATE_MISSING");
		assertThat(result.errorMessage()).contains("authStateUsed=false");
	}

	@Test
	void extractsAuthorTextDateLanguageAndMediaFromPostContainer() {
		Element article = article("""
				<article lang="az">
				  <div data-ad-rendering-role="profile_name">
				    <a role="link" href="/example"><strong>Example Page</strong></a>
				    <img src="https://scontent.xx.fbcdn.net/avatar.jpg" alt="profile picture">
				  </div>
				  <a role="link" href="/example/posts/123456789" aria-label="Today at 13:40">2 h</a>
				  <div data-ad-rendering-role="story_message">
				    Main post text #tag @mention
				    <span role="button">See more</span>
				  </div>
				  <div aria-label="Like">Like</div>
				  <img src="https://scontent.xx.fbcdn.net/post-photo.jpg" alt="Court photo">
				  <img src="https://static.xx.fbcdn.net/rsrc.php/icon.png" alt="icon">
				  <video poster="https://scontent.xx.fbcdn.net/post-video-poster.jpg" src="blob:https://www.facebook.com/video"></video>
				</article>
				""");
		FacebookScrapeDiagnostics diagnostics = new FacebookScrapeDiagnostics();

		FacebookScraperAdapter.ParseAttempt attempt = adapter.parseContainer(article, context(Language.RU), "Məhkəmə", diagnostics);

		assertThat(attempt.skipReason()).isNull();
		assertThat(attempt.candidate().externalPostId()).isEqualTo("123456789");
		assertThat(attempt.candidate().postUrl()).isEqualTo("https://www.facebook.com/example/posts/123456789");
		assertThat(attempt.candidate().postDate()).isEqualTo(OffsetDateTime.parse("2026-07-17T13:40:00+04:00"));
		assertThat(attempt.candidate().author().externalId()).isEqualTo("example");
		assertThat(attempt.candidate().author().username()).isEqualTo("example");
		assertThat(attempt.candidate().author().displayName()).isEqualTo("Example Page");
		assertThat(attempt.candidate().author().profileUrl()).isEqualTo("https://www.facebook.com/example");
		assertThat(attempt.candidate().text()).contains("Main post text").doesNotContain("Like");
		assertThat(attempt.candidate().language()).isEqualTo("az");
		assertThat(attempt.candidate().media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly(
						"https://scontent.xx.fbcdn.net/post-photo.jpg",
						"https://scontent.xx.fbcdn.net/post-video-poster.jpg"
				);
		assertThat(attempt.candidate().media())
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE, MediaType.IMAGE);
		assertThat(diagnostics.toMetadata()).containsEntry("imagesCollected", 2);
	}

	@Test
	void collectsMediaOnlyPosts() {
		Element article = article("""
				<article>
				  <div data-ad-rendering-role="profile_name"><a role="link" href="/example">Example Page</a></div>
				  <a href="/example/posts/123456789" aria-label="Today at 13:40">2 h</a>
				  <img src="https://scontent.xx.fbcdn.net/post-photo.jpg" alt="Court photo">
				</article>
				""");

		FacebookScraperAdapter.ParseAttempt attempt = adapter.parseContainer(article, context(Language.EN), "court", new FacebookScrapeDiagnostics());

		assertThat(attempt.candidate()).isNotNull();
		assertThat(attempt.candidate().text()).isEmpty();
		assertThat(attempt.candidate().media()).hasSize(1);
	}

	@Test
	void skipsSponsoredAndReelsByDefault() {
		Element sponsored = article("""
				<article>
				  <div data-ad-rendering-role="profile_name"><a role="link" href="/brand">Brand</a></div>
				  <span>Sponsored</span>
				  <a href="/brand/posts/123" aria-label="Today at 13:40">2 h</a>
				  <div data-ad-rendering-role="story_message">Ad text</div>
				</article>
				""");
		Element reel = article("""
				<article>
				  <div data-ad-rendering-role="profile_name"><a role="link" href="/creator">Creator</a></div>
				  <a href="/reel/999" aria-label="Today at 13:40">2 h</a>
				  <div data-ad-rendering-role="story_message">Reel text</div>
				</article>
				""");

		assertThat(adapter.parseContainer(sponsored, context(Language.EN), "brand", new FacebookScrapeDiagnostics()).skipReason())
				.isEqualTo(FacebookScraperAdapter.SkipReason.SPONSORED);
		assertThat(adapter.parseContainer(reel, context(Language.EN), "brand", new FacebookScrapeDiagnostics()).skipReason())
				.isEqualTo(FacebookScraperAdapter.SkipReason.REEL_DISABLED);
	}

	private Element article(String html) {
		return Jsoup.parse(html, FacebookScraperSupport.BASE_URL).selectFirst("article");
	}

	private ScraperExecutionContext context(Language language) {
		Source source = Source.builder()
				.id(1)
				.code("FACEBOOK")
				.type(SourceType.SOCIAL)
				.baseUrl("https://www.facebook.com/")
				.build();
		Keyword keyword = Keyword.builder()
				.id(1)
				.word("court")
				.language(language)
				.build();
		ScrapeJob job = ScrapeJob.builder()
				.id(UUID.randomUUID())
				.source(source)
				.keyword(keyword)
				.build();
		return new ScraperExecutionContext(
				job,
				source,
				keyword,
				OffsetDateTime.parse("2026-07-17T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-17T23:59:59+04:00"),
				5,
				100,
				Map.of()
		);
	}
}
