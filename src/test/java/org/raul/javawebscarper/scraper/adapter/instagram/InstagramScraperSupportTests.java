package org.raul.javawebscarper.scraper.adapter.instagram;

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

class InstagramScraperSupportTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private final InstagramDateParser dateParser = new InstagramDateParser(
			BAKU,
			Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), BAKU)
	);

	@Test
	void supportsInstagramSourceAndAliases() {
		assertThat(InstagramScraperSupport.supports(Source.builder().code("INSTAGRAM").build())).isTrue();
		assertThat(InstagramScraperSupport.supports(Source.builder().code("IG").build())).isTrue();
		assertThat(InstagramScraperSupport.supports(Source.builder().code("social").baseUrl("https://www.instagram.com/").build())).isTrue();
		assertThat(InstagramScraperSupport.supports(Source.builder().code("FACEBOOK").baseUrl("https://www.facebook.com/").build())).isFalse();
	}

	@Test
	void discoversPostCandidatesAndSkipsReelsByDefault() {
		Document document = Jsoup.parse("""
				<a href="/p/POST123/?igsh=abc"><img src="https://cdninstagram.com/thumb1.jpg"></a>
				<a href="/p/POST123/?utm_source=copy"></a>
				<a href="/reel/REEL123/"><img src="https://cdninstagram.com/reel.jpg"></a>
				""", InstagramScraperSupport.BASE_URL);
		InstagramScrapeDiagnostics diagnostics = new InstagramScrapeDiagnostics();

		List<InstagramPostCandidate> candidates = InstagramScraperSupport.discoverPostCandidates(document, false, 10, diagnostics);

		assertThat(candidates).hasSize(1);
		assertThat(candidates.getFirst().externalPostId()).isEqualTo("POST123");
		assertThat(candidates.getFirst().postUrl()).isEqualTo("https://www.instagram.com/p/POST123/");
		assertThat(candidates.getFirst().thumbnailUrl()).isEqualTo("https://cdninstagram.com/thumb1.jpg");
		assertThat(diagnostics.duplicateCandidatesSkipped).isEqualTo(1);
		assertThat(diagnostics.reelsSkipped).isEqualTo(1);
	}

	@Test
	void extractsAuthorCaptionDateLanguageAndMediaFromScopedPostFixture() {
		Document document = Jsoup.parse("""
				<main>
				  <article lang="az">
				    <header>
				      <a href="/mainauthor/"><span>mainauthor</span></a>
				      <a href="/collab/"><span>collab</span></a>
				      <img src="https://cdninstagram.com/avatar.jpg" alt="mainauthor profile picture">
				    </header>
				    <div>
				      <img src="https://cdninstagram.com/small.jpg 320w, https://cdninstagram.com/main.jpg 1080w" srcset="https://cdninstagram.com/small.jpg 320w, https://cdninstagram.com/main.jpg 1080w" alt="Court image">
				      <video src="blob:https://www.instagram.com/video" poster="https://cdninstagram.com/poster.jpg"></video>
				    </div>
				    <time datetime="2026-07-14T10:30:00.000Z">2d</time>
				    <h1 dir="auto">mainauthor Main caption #tag @mention</h1>
				    <ul>
				      <li><a href="/commenter/">commenter</a><span dir="auto">A comment that must not win</span><img src="https://cdninstagram.com/comment-avatar.jpg" alt="profile picture"></li>
				    </ul>
				    <span>Like</span><span>View replies</span>
				  </article>
				  <aside><img src="https://cdninstagram.com/suggested.jpg"></aside>
				</main>
				""", InstagramScraperSupport.BASE_URL);
		InstagramPostCandidate candidate = new InstagramPostCandidate(
				"POST123",
				"https://www.instagram.com/p/POST123/",
				InstagramPostType.POST,
				"https://cdninstagram.com/thumb.jpg"
		);
		InstagramScrapeDiagnostics diagnostics = new InstagramScrapeDiagnostics();

		ScrapedPostDTO post = InstagramScraperSupport.extractPost(
				document,
				context(Language.RU),
				candidate,
				dateParser,
				diagnostics,
				new InstagramProperties()
		).orElseThrow();

		assertThat(post.externalPostId()).isEqualTo("POST123");
		assertThat(post.postDate()).isEqualTo(OffsetDateTime.parse("2026-07-14T14:30:00+04:00"));
		assertThat(post.author().externalId()).isEqualTo("mainauthor");
		assertThat(post.author().username()).isEqualTo("mainauthor");
		assertThat(post.author().profileUrl()).isEqualTo("https://www.instagram.com/mainauthor/");
		assertThat(post.text()).isEqualTo("Main caption #tag @mention");
		assertThat(post.text()).doesNotContain("comment").doesNotContain("Like");
		assertThat(post.language()).isEqualTo("az");
		assertThat(post.media())
				.extracting(ScrapedMediaDTO::mediaUrl)
				.containsExactly("https://cdninstagram.com/main.jpg", "https://cdninstagram.com/poster.jpg");
		assertThat(post.media())
				.extracting(ScrapedMediaDTO::mediaType)
				.containsExactly(MediaType.IMAGE, MediaType.IMAGE);
		assertThat(post.metadata()).containsEntry("postType", "POST");
		assertThat(post.metadata()).containsEntry("isCarousel", true);
		assertThat(post.metadata()).containsEntry("hasVideo", true);
		assertThat(post.metadata()).containsEntry("collaboratorUsernames", List.of("collab"));
		assertThat(diagnostics.imagesCollected).isEqualTo(2);
	}

	@Test
	void skipsSponsoredPostsWhenDisabled() {
		Document document = Jsoup.parse("""
				<article>
				  <header><a href="/brand/">brand</a></header>
				  <span>Sponsored</span>
				  <time datetime="2026-07-14T10:30:00.000Z"></time>
				  <h1 dir="auto">brand ad text</h1>
				</article>
				""", InstagramScraperSupport.BASE_URL);

		assertThat(InstagramScraperSupport.extractPost(
				document,
				context(Language.EN),
				new InstagramPostCandidate("AD123", "https://www.instagram.com/p/AD123/", InstagramPostType.POST, null),
				dateParser,
				new InstagramScrapeDiagnostics(),
				new InstagramProperties()
		)).isEmpty();
	}

	private ScraperExecutionContext context(Language language) {
		Source source = Source.builder()
				.id(1)
				.code("INSTAGRAM")
				.type(SourceType.SOCIAL)
				.baseUrl("https://www.instagram.com/")
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
}
