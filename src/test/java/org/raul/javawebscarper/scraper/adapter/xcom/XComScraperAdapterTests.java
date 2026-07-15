package org.raul.javawebscarper.scraper.adapter.xcom;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.model.enumerated.SourceType;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class XComScraperAdapterTests {

	private final XComProperties properties = new XComProperties();
	private final XComScraperAdapter adapter = new XComScraperAdapter(
			null,
			properties,
			new XComSearchQueryBuilder(ZoneId.of("Asia/Baku")),
			new XComDateParser(),
			new XAuthenticationVerifier()
	);

	@Test
	void supportsXComSourceAndLegacyAliases() {
		assertThat(adapter.sourceCode()).isEqualTo("X_COM");
		assertThat(adapter.supports(Source.builder().code("X_COM").baseUrl("https://x.com/").build())).isTrue();
		assertThat(adapter.supports(Source.builder().code("TWITTER").baseUrl("https://twitter.com/").build())).isTrue();
	}

	@Test
	void extractsTweetAuthorTextLanguageAndMediaFromArticle() {
		Element article = article("""
				<article data-testid="tweet">
				  <div data-testid="User-Name">
				    <a href="/realUser"><span>Real Name</span><span>@realUser</span></a>
				  </div>
				  <img src="https://pbs.twimg.com/profile_images/123/avatar.jpg">
				  <a href="/realUser/status/123456789/photo/1">
				    <time datetime="2026-07-14T08:30:00.000Z"></time>
				  </a>
				  <div data-testid="tweetText" lang="az">Line one #tag @mention
				  Line two</div>
				  <div data-testid="tweetPhoto"><img src="https://pbs.twimg.com/media/photo1.jpg"></div>
				  <div data-testid="tweetPhoto"><img src="https://pbs.twimg.com/media/photo1.jpg"></div>
				  <img src="https://abs.twimg.com/emoji/v2/72x72/1f600.png">
				  <video poster="https://pbs.twimg.com/ext_tw_video_thumb/video.jpg" src="blob:https://x.com/video"></video>
				  <article>
				    <div data-testid="User-Name">Quoted Author @quoted</div>
				    <div data-testid="tweetText">quoted text must not win over the main tweet text</div>
				  </article>
				</article>
				""");

		XComScraperAdapter.ParseAttempt attempt = adapter.parseArticle(article, context(Language.RU), "court", searchUrl(), new XScrapeDiagnostics());

		assertThat(attempt.skipReason()).isNull();
		assertThat(attempt.candidate().externalPostId()).isEqualTo("123456789");
		assertThat(attempt.candidate().postUrl()).isEqualTo("https://x.com/realUser/status/123456789");
		assertThat(attempt.candidate().postDate()).isEqualTo(OffsetDateTime.parse("2026-07-14T08:30:00Z"));
		assertThat(attempt.candidate().author().externalId()).isEqualTo("realUser");
		assertThat(attempt.candidate().author().username()).isEqualTo("realUser");
		assertThat(attempt.candidate().author().displayName()).isEqualTo("Real Name");
		assertThat(attempt.candidate().author().profileUrl()).isEqualTo("https://x.com/realUser");
		assertThat(attempt.candidate().author().avatarUrl()).isEqualTo("https://pbs.twimg.com/profile_images/123/avatar.jpg");
		assertThat(attempt.candidate().text()).contains("Line one").contains("Line two");
		assertThat(attempt.candidate().language()).isEqualTo("az");
		assertThat(attempt.candidate().media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly(
						"https://pbs.twimg.com/media/photo1.jpg",
						"https://pbs.twimg.com/ext_tw_video_thumb/video.jpg"
				);
		assertThat(attempt.candidate().media())
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE, MediaType.VIDEO);
	}

	@Test
	void skipsPromotedTweetWhenPromotedPostsAreDisabled() {
		Element article = article("""
				<article data-testid="tweet">
				  <span>Promoted</span>
				  <a href="/brand/status/123"><time datetime="2026-07-14T08:30:00.000Z"></time></a>
				  <div data-testid="tweetText" lang="en">Advertisement text</div>
				</article>
				""");

		XComScraperAdapter.ParseAttempt attempt = adapter.parseArticle(article, context(Language.EN), "brand", searchUrl(), new XScrapeDiagnostics());

		assertThat(attempt.candidate()).isNull();
		assertThat(attempt.skipReason()).isEqualTo(XComScraperAdapter.SkipReason.PROMOTED);
	}

	@Test
	void usesKeywordLanguageWhenTweetTextLangIsMissing() {
		Element article = article("""
				<article data-testid="tweet">
				  <div data-testid="User-Name">User @realUser</div>
				  <a href="/realUser/status/123456789"><time datetime="2026-07-14T08:30:00.000Z"></time></a>
				  <div data-testid="tweetText">Russian keyword text</div>
				</article>
				""");

		XComScraperAdapter.ParseAttempt attempt = adapter.parseArticle(article, context(Language.RU), "sud", searchUrl(), new XScrapeDiagnostics());

		assertThat(attempt.candidate().language()).isEqualTo("ru");
	}

	@Test
	void skipsArticleWithoutStatusDate() {
		Element article = article("""
				<article data-testid="tweet">
				  <a href="/realUser/status/123456789"><time datetime=""></time></a>
				  <div data-testid="tweetText">Tweet text</div>
				</article>
				""");

		XComScraperAdapter.ParseAttempt attempt = adapter.parseArticle(article, context(Language.EN), "court", searchUrl(), new XScrapeDiagnostics());

		assertThat(attempt.candidate()).isNull();
		assertThat(attempt.skipReason()).isEqualTo(XComScraperAdapter.SkipReason.INVALID_DATE);
	}

	@Test
	void collectsMediaOnlyPosts() {
		Element article = article("""
				<article data-testid="tweet">
				  <div data-testid="User-Name">User @realUser</div>
				  <a href="/realUser/status/123456789"><time datetime="2026-07-14T08:30:00.000Z"></time></a>
				  <div data-testid="tweetPhoto"><img src="https://pbs.twimg.com/media/photo1.jpg"></div>
				</article>
				""");
		XScrapeDiagnostics diagnostics = new XScrapeDiagnostics();

		XComScraperAdapter.ParseAttempt attempt = adapter.parseArticle(article, context(Language.EN), "court", searchUrl(), diagnostics);

		assertThat(attempt.candidate()).isNotNull();
		assertThat(attempt.candidate().text()).isEmpty();
		assertThat(attempt.candidate().media()).hasSize(1);
		assertThat(diagnostics.toMetadata()).containsEntry("mediaOnlyPostsCollected", 1);
	}

	private Element article(String html) {
		return Jsoup.parse(html, "https://x.com/search")
				.selectFirst("article[data-testid=tweet]");
	}

	private ScraperExecutionContext context(Language language) {
		Source source = Source.builder()
				.id(1)
				.code("X_COM")
				.type(SourceType.SOCIAL)
				.baseUrl("https://x.com/")
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
				OffsetDateTime.parse("2026-07-14T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-14T23:59:59+04:00"),
				5,
				100,
				Map.of()
		);
	}

	private String searchUrl() {
		return "https://x.com/search?f=live&q=court+since%3A2026-07-14+until%3A2026-07-15&src=typed_query";
	}
}
