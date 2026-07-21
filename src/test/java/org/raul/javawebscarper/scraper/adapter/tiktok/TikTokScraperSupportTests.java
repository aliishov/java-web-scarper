package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.model.enumerated.SourceType;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TikTokScraperSupportTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private final TikTokDateParser dateParser = new TikTokDateParser(
			BAKU,
			Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), BAKU)
	);

	@Test
	void supportsTikTokSourceAndAliases() {
		assertThat(TikTokScraperSupport.supports(Source.builder().code("TIKTOK").build())).isTrue();
		assertThat(TikTokScraperSupport.supports(Source.builder().code("TT").build())).isTrue();
		assertThat(TikTokScraperSupport.supports(Source.builder().code("social").baseUrl("https://www.tiktok.com/").build())).isTrue();
		assertThat(TikTokScraperSupport.supports(Source.builder().code("INSTAGRAM").baseUrl("https://www.instagram.com/").build())).isFalse();
	}

	@Test
	void discoversCanonicalVideoCandidatesAndKeepsVirtualizedDedupState() {
		Document document = Jsoup.parse("""
				<main>
				  <article><a href="/@aznews/video/7351234567890123456?web_id=secret"><img src="https://p16-sign.tiktokcdn.com/thumb1.jpg"></a></article>
				  <article><a href="https://www.tiktok.com/@aznews/video/7351234567890123456?refer=copy"></a></article>
				  <article><a href="/@other/video/7351234567890123457"><img src="https://p16-sign.tiktokcdn.com/thumb2.jpg"></a></article>
				  <a href="/@user">user profile</a>
				</main>
				""", TikTokScraperSupport.BASE_URL);
		TikTokScrapeDiagnostics diagnostics = new TikTokScrapeDiagnostics();

		List<TikTokPostCandidate> candidates = TikTokScraperSupport.discoverPostCandidates(document, 10, diagnostics);

		assertThat(candidates).hasSize(2);
		assertThat(candidates.getFirst().externalPostId()).isEqualTo("7351234567890123456");
		assertThat(candidates.getFirst().postUrl()).isEqualTo("https://www.tiktok.com/@aznews/video/7351234567890123456");
		assertThat(candidates.getFirst().thumbnailUrl()).isEqualTo("https://p16-sign.tiktokcdn.com/thumb1.jpg");
		assertThat(diagnostics.duplicateCandidatesSkipped).isEqualTo(1);
	}

	@Test
	void extractsAuthorCaptionDateLanguageAndMediaFromScopedPostFixture() {
		Document document = Jsoup.parse("""
				<html lang="en">
				  <head>
				    <meta property="og:description" content="Fallback meta caption">
				  </head>
				  <body>
				    <main lang="az">
				      <article data-e2e="browse-video">
				        <header>
				          <a href="/@mainauthor"><strong>Main Display</strong></a>
				          <img src="https://p16-sign.tiktokcdn.com/avatar.jpg" alt="mainauthor avatar">
				        </header>
				        <a href="/@mainauthor/video/7351234567890123456">canonical</a>
				        <time datetime="2026-07-15T08:00:00Z">2d ago</time>
				        <div data-e2e="browse-video-desc">@mainauthor Main caption #tag @mention</div>
				        <div data-e2e="comment"><span>commenter text must not be included</span><img src="https://p16-sign.tiktokcdn.com/comment-avatar.jpg" alt="comment avatar"></div>
				        <a href="/music/original-sound-123">Original sound - musician</a>
				        <video src="blob:https://www.tiktok.com/video" poster="https://p16-sign.tiktokcdn.com/poster.jpg">
				          <source src="https://v16-webapp.tiktokcdn.com/video.mp4">
				        </video>
				        <img src="https://p16-sign.tiktokcdn.com/tiktok-logo.jpg" alt="TikTok logo">
				      </article>
				      <aside><img src="https://p16-sign.tiktokcdn.com/suggested.jpg"></aside>
				    </main>
				  </body>
				</html>
				""", TikTokScraperSupport.BASE_URL);
		TikTokPostCandidate candidate = new TikTokPostCandidate(
				"7351234567890123456",
				"https://www.tiktok.com/@mainauthor/video/7351234567890123456",
				"mainauthor",
				"https://p16-sign.tiktokcdn.com/thumb.jpg",
				null,
				"preview"
		);
		TikTokScrapeDiagnostics diagnostics = new TikTokScrapeDiagnostics();
		diagnostics.searchMode = TikTokSearchMode.KEYWORD;
		diagnostics.anonymousSession = true;

		ScrapedPostDTO post = TikTokScraperSupport.extractPost(
				document,
				context(Language.RU),
				candidate,
				dateParser,
				diagnostics,
				new TikTokProperties()
		).orElseThrow();

		assertThat(post.externalPostId()).isEqualTo("7351234567890123456");
		assertThat(post.postUrl()).isEqualTo("https://www.tiktok.com/@mainauthor/video/7351234567890123456");
		assertThat(post.postDate()).isEqualTo(OffsetDateTime.parse("2026-07-15T12:00:00+04:00"));
		assertThat(post.author().externalId()).isEqualTo("mainauthor");
		assertThat(post.author().username()).isEqualTo("mainauthor");
		assertThat(post.author().displayName()).isEqualTo("Main Display");
		assertThat(post.author().profileUrl()).isEqualTo("https://www.tiktok.com/@mainauthor");
		assertThat(post.text()).isEqualTo("Main caption #tag @mention");
		assertThat(post.text()).doesNotContain("commenter").doesNotContain("Original sound");
		assertThat(post.language()).isEqualTo("az");
		assertThat(post.media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://v16-webapp.tiktokcdn.com/video.mp4", "https://p16-sign.tiktokcdn.com/poster.jpg");
		assertThat(post.media())
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.VIDEO, MediaType.IMAGE);
		assertThat(post.metadata()).containsEntry("source", "TIKTOK");
		assertThat(post.metadata()).containsEntry("keyword", "Məhkəmə");
		assertThat(post.metadata()).containsEntry("hasVideo", true);
		assertThat(post.metadata()).containsEntry("videoUrlUnavailable", false);
		assertThat(post.metadata()).containsEntry("captionSource", "PRIMARY_DOM");
		assertThat(post.metadata()).containsEntry("dateSource", "ISO_INSTANT");
		assertThat(post.metadata()).containsEntry("anonymousSession", true);
		assertThat(diagnostics.videoUrlUnavailable).isZero();
	}

	@Test
	void fallsBackToMetaCaptionForTextEmptyRegression() {
		Document document = Jsoup.parse("""
				<html>
				  <head><meta property="og:description" content="TikTok video from mainauthor: &quot;Meta caption #court&quot;."></head>
				  <body>
				    <main>
				      <article data-e2e="browse-video">
				        <a href="/@mainauthor">mainauthor</a>
				        <a href="/@mainauthor/video/7351234567890123456">canonical</a>
				        <time datetime="2026-07-15T08:00:00Z"></time>
				        <video poster="https://p16-sign.tiktokcdn.com/poster.jpg"></video>
				      </article>
				    </main>
				  </body>
				</html>
				""", TikTokScraperSupport.BASE_URL);

		ScrapedPostDTO post = TikTokScraperSupport.extractPost(
				document,
				context(Language.EN),
				new TikTokPostCandidate("7351234567890123456", "https://www.tiktok.com/@mainauthor/video/7351234567890123456", "mainauthor", null, null, null),
				dateParser,
				new TikTokScrapeDiagnostics(),
				new TikTokProperties()
		).orElseThrow();

		assertThat(post.text()).isEqualTo("Meta caption #court");
		assertThat(post.text()).isNotBlank();
	}

	@Test
	void skipsSponsoredPostsWhenDisabled() {
		Document document = Jsoup.parse("""
				<main>
				  <article>
				    <a href="/@brand">brand</a>
				    <a href="/@brand/video/7351234567890123456">canonical</a>
				    <span>Sponsored</span>
				    <time datetime="2026-07-15T08:00:00Z"></time>
				    <div data-e2e="video-desc">ad caption</div>
				  </article>
				</main>
				""", TikTokScraperSupport.BASE_URL);

		assertThat(TikTokScraperSupport.extractPost(
				document,
				context(Language.EN),
				new TikTokPostCandidate("7351234567890123456", "https://www.tiktok.com/@brand/video/7351234567890123456", "brand", null, null, null),
				dateParser,
				new TikTokScrapeDiagnostics(),
				new TikTokProperties()
		)).isEmpty();
	}

	private ScraperExecutionContext context(Language language) {
		Source source = Source.builder()
				.id(1)
				.code("TIKTOK")
				.type(SourceType.SOCIAL)
				.baseUrl("https://www.tiktok.com/")
				.build();
		Keyword keyword = Keyword.builder()
				.id(1)
				.word("Məhkəmə")
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
				OffsetDateTime.parse("2026-07-15T00:00:00+04:00"),
				OffsetDateTime.parse("2026-07-15T23:59:59+04:00"),
				5,
				100,
				Map.of()
		);
	}
}
