package org.raul.javawebscarper.scraper.adapter.instagram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.raul.javawebscarper.browser.BrowserEngineException;
import org.raul.javawebscarper.browser.BrowserPage;
import org.raul.javawebscarper.browser.BrowserSession;
import org.raul.javawebscarper.browser.BrowserSessionFactory;
import org.raul.javawebscarper.browser.BrowserSessionOptions;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.ScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionStatus;
import org.raul.javawebscarper.scraper.support.DateRangeValidator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstagramScraperAdapter implements ScraperAdapter {

	private static final int MIN_KEYWORD_LENGTH = 1;
	private static final int SCROLL_PIXELS = 1_100;

	private final BrowserSessionFactory browserSessionFactory;
	private final InstagramProperties properties;
	private final InstagramSearchQueryBuilder searchQueryBuilder;
	private final InstagramDateParser dateParser;
	private final InstagramAuthenticationVerifier authenticationVerifier;

	@Override
	public String sourceCode() {
		return InstagramScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return InstagramScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		if (!properties.isEnabled()) {
			return ScraperExecutionResult.failed("INSTAGRAM_SCRAPER_DISABLED: Instagram scraper is disabled");
		}
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("INSTAGRAM_BAD_REQUEST: search keyword must not be blank");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());

		InstagramScrapeDiagnostics diagnostics = new InstagramScrapeDiagnostics();
		diagnostics.searchMode = searchQueryBuilder.resolveMode(keyword.trim(), properties.getSearchMode());
		String searchUrl = searchQueryBuilder.buildSearchUrl(properties.getBaseUrl(), keyword.trim(), properties.getSearchMode());
		diagnostics.searchQuery = searchUrl;
		diagnostics.hashtagFallbackUsed = diagnostics.searchMode == InstagramSearchMode.HASHTAG && !keyword.trim().startsWith("#");
		long deadlineNanos = System.nanoTime() + properties.getMaxRunDurationMs() * 1_000_000L;

		BrowserSessionOptions sessionOptions;
		try {
			sessionOptions = sessionOptions(diagnostics);
		} catch (BrowserEngineException exception) {
			log.warn("Instagram auth state is not usable: {}", exception.getMessage());
			diagnostics.authenticationStatus = InstagramAuthenticationStatus.AUTH_STATE_MISSING;
			return failed(authenticationFailureMessage(InstagramAuthenticationStatus.AUTH_STATE_MISSING), diagnostics);
		}

		log.info(
				"Starting Instagram scraping: keyword={}, dateFrom={}, dateTo={}, mode={}, authStateUsed={}, maxScrollAttempts={}, maxPosts={}",
				keyword,
				context.dateFrom(),
				context.dateTo(),
				diagnostics.searchMode,
				diagnostics.authStateUsed,
				properties.getMaxScrollAttempts(),
				context.maxPosts()
		);

		try (BrowserSession session = browserSessionFactory.createSession(sessionOptions)) {
			BrowserPage searchPage = session.newPage();
			if (properties.isAuthenticationRequired()) {
				InstagramAuthenticationStatus authenticationStatus = verifyAuthenticatedSession(searchPage);
				diagnostics.authenticationStatus = authenticationStatus;
				if (authenticationStatus != InstagramAuthenticationStatus.AUTHENTICATED) {
					incrementAuthenticationDiagnostic(authenticationStatus, diagnostics);
					return failed(authenticationFailureMessage(authenticationStatus), diagnostics);
				}
			}
			openSearchPage(searchPage, keyword.trim(), searchUrl, diagnostics.searchMode);

			InstagramAuthenticationStatus searchPageStatus = InstagramAuthenticationPageInspector.inspect(searchPage.url(), searchPage.content());
			if (isHardFailure(searchPageStatus)) {
				diagnostics.authenticationStatus = searchPageStatus;
				incrementAuthenticationDiagnostic(searchPageStatus, diagnostics);
				diagnostics.authenticationExpiredDuringRun = true;
				return failed(authenticationFailureMessage(searchPageStatus), diagnostics);
			}

			List<InstagramPostCandidate> candidates = collectCandidates(searchPage, context, diagnostics, deadlineNanos);
			if (candidates.isEmpty()
					&& diagnostics.searchMode == InstagramSearchMode.KEYWORD
					&& !diagnostics.timedOut
					&& searchQueryBuilder.hashtagFallbackAllowed(keyword.trim())) {
				candidates = collectHashtagFallbackCandidates(searchPage, context, keyword.trim(), diagnostics, deadlineNanos);
			}
			if (candidates.isEmpty()) {
				return emptyOrGridFailure(searchPage, diagnostics);
			}
			List<ScrapedPostDTO> posts = collectPosts(searchPage, context, candidates, diagnostics, deadlineNanos);
			if (posts.isEmpty()
					&& diagnostics.searchMode == InstagramSearchMode.KEYWORD
					&& !diagnostics.timedOut
					&& searchQueryBuilder.hashtagFallbackAllowed(keyword.trim())) {
				log.info("Instagram keyword candidates produced no in-range posts; trying hashtag fallback: keyword={}", keyword);
				candidates = collectHashtagFallbackCandidates(searchPage, context, keyword.trim(), diagnostics, deadlineNanos);
				if (!candidates.isEmpty()) {
					posts = collectPosts(searchPage, context, candidates, diagnostics, deadlineNanos);
				}
			}

			log.info(
					"Finished Instagram scraping: keyword={}, postsCollected={}, candidates={}, duplicates={}, reelsSkipped={}, "
							+ "sponsoredSkipped={}, tooNew={}, tooOld={}, dateFailures={}, postLoadFailures={}, scrollAttempts={}, noNewIterations={}, "
							+ "challenge={}, rateLimit={}",
					keyword,
					posts.size(),
					diagnostics.uniquePostCandidates,
					diagnostics.duplicateCandidatesSkipped,
					diagnostics.reelsSkipped,
					diagnostics.sponsoredPostsSkipped,
					diagnostics.tooNewSkipped,
					diagnostics.tooOldSkipped,
					diagnostics.dateParseFailures,
					diagnostics.postLoadFailures,
					diagnostics.scrollAttempts,
					diagnostics.noNewPostIterations,
					diagnostics.challengeDetected,
					diagnostics.rateLimitDetected
			);
			if (!posts.isEmpty()) {
				return ScraperExecutionResult.success(posts);
			}
			if (diagnostics.postsOpened > 0 && diagnostics.extractionFailures >= diagnostics.postsOpened) {
				return failed("INSTAGRAM_EXTRACTION_FAILED: candidates were found but no valid posts were extracted", diagnostics);
			}
			return ScraperExecutionResult.empty();
		} catch (BrowserEngineException exception) {
			log.warn("Instagram scraping failed: {}", exception.getMessage());
			return failed("INSTAGRAM_POST_LOAD_FAILED: " + exception.getMessage(), diagnostics);
		} catch (RuntimeException exception) {
			log.error("Unexpected Instagram scraping failure", exception);
			return failed("INSTAGRAM_EXTRACTION_FAILED: " + exception.getMessage(), diagnostics);
		}
	}

	private List<InstagramPostCandidate> collectHashtagFallbackCandidates(
			BrowserPage page,
			ScraperExecutionContext context,
			String keyword,
			InstagramScrapeDiagnostics diagnostics,
			long deadlineNanos
	) {
		if (isRunTimedOut(deadlineNanos, diagnostics)) {
			return List.of();
		}
		String hashtagUrl = searchQueryBuilder.buildSearchUrl(properties.getBaseUrl(), keyword, InstagramSearchMode.HASHTAG);
		log.info("Trying Instagram hashtag fallback: keyword={}, url={}", keyword, hashtagUrl);
		diagnostics.searchMode = InstagramSearchMode.HASHTAG;
		diagnostics.hashtagFallbackUsed = true;
		diagnostics.searchQuery = hashtagUrl;
		diagnostics.noNewPostIterations = 0;
		page.navigate(hashtagUrl);
		page.waitForSelector(InstagramSelectors.BODY, properties.getTimelineLoadTimeoutMs());
		page.waitForTimeout(properties.getActionDelayMs());
		return collectCandidates(page, context, diagnostics, deadlineNanos);
	}

	private BrowserSessionOptions sessionOptions(InstagramScrapeDiagnostics diagnostics) {
		String authStatePath = properties.getAuthStatePath();
		if (authStatePath == null || authStatePath.isBlank()) {
			if (properties.isAuthenticationRequired()) {
				throw new BrowserEngineException("Instagram authentication state is not configured");
			}
			return BrowserSessionOptions.defaults();
		}
		Path path = Path.of(authStatePath.trim()).toAbsolutePath().normalize();
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			throw new BrowserEngineException("Instagram authentication state file is missing or unreadable");
		}
		diagnostics.authStateUsed = true;
		return new BrowserSessionOptions(path, properties.getLocale(), properties.getTimezoneId(), null, Map.of());
	}

	private InstagramAuthenticationStatus verifyAuthenticatedSession(BrowserPage page) {
		page.navigate(authVerificationUrl());
		page.waitForSelector(InstagramSelectors.BODY, properties.getLoginTimeoutMs());
		page.waitForTimeout(properties.getActionDelayMs());
		return authenticationVerifier.verify(page);
	}

	private String authVerificationUrl() {
		String url = properties.getAuthVerificationUrl() == null || properties.getAuthVerificationUrl().isBlank()
				? InstagramScraperSupport.BASE_URL
				: properties.getAuthVerificationUrl().trim();
		return url.endsWith("/") ? url : url + "/";
	}

	private void openSearchPage(BrowserPage page, String keyword, String searchUrl, InstagramSearchMode searchMode) {
		if (searchMode == InstagramSearchMode.KEYWORD) {
			try {
				page.navigate(homeUrl());
				page.waitForSelector(InstagramSelectors.BODY, properties.getTimelineLoadTimeoutMs());
				page.waitForTimeout(properties.getActionDelayMs());
				page.click(InstagramSelectors.SEARCH_NAVIGATION);
				page.waitForTimeout(properties.getActionDelayMs());
				page.fill(InstagramSelectors.SEARCH_INPUT, keyword);
				page.press(InstagramSelectors.SEARCH_INPUT, "Enter");
				page.waitForTimeout(properties.getScrollDelayMs());
				if (page.url() != null && page.url().contains("/explore/search/")) {
					return;
				}
				log.warn("Instagram UI search did not navigate to usable results; using direct search URL fallback");
			} catch (BrowserEngineException exception) {
				log.warn("Instagram UI search failed; using direct search URL fallback: {}", exception.getMessage());
			}
		}
		page.navigate(searchUrl);
		page.waitForSelector(InstagramSelectors.BODY, properties.getTimelineLoadTimeoutMs());
		page.waitForTimeout(properties.getActionDelayMs());
	}

	private List<InstagramPostCandidate> collectCandidates(
			BrowserPage page,
			ScraperExecutionContext context,
			InstagramScrapeDiagnostics diagnostics,
			long deadlineNanos
	) {
		LinkedHashMap<String, InstagramPostCandidate> candidates = new LinkedHashMap<>();
		int targetMaxPosts = effectiveMaxPosts(context);
		int maxCandidates = Math.min(properties.getMaxCandidates(), Math.max(targetMaxPosts * 3, targetMaxPosts));
		for (int attempt = 0;
				attempt < Math.min(properties.getMaxScrollAttempts(), context.maxPages() * properties.getMaxScrollAttempts())
						&& candidates.size() < maxCandidates
						&& diagnostics.noNewPostIterations < properties.getNoNewPostLimit()
						&& !isRunTimedOut(deadlineNanos, diagnostics);
				attempt++) {
			Document document = Jsoup.parse(page.content(), page.url() == null ? InstagramScraperSupport.BASE_URL : page.url());
			InstagramAuthenticationStatus pageStatus = InstagramAuthenticationPageInspector.inspect(page.url(), document.html());
			if (isHardFailure(pageStatus)) {
				diagnostics.authenticationStatus = pageStatus;
				incrementAuthenticationDiagnostic(pageStatus, diagnostics);
				diagnostics.authenticationExpiredDuringRun = true;
				break;
			}
			int before = candidates.size();
			for (InstagramPostCandidate candidate : InstagramScraperSupport.discoverPostCandidates(
					document,
					properties.isIncludeReels(),
					maxCandidates,
					diagnostics
			)) {
				candidates.putIfAbsent(candidate.externalPostId(), candidate);
			}
			diagnostics.uniquePostCandidates = candidates.size();
			if (candidates.size() == before) {
				diagnostics.noNewPostIterations++;
			} else {
				diagnostics.noNewPostIterations = 0;
			}
			if (candidates.size() >= maxCandidates) {
				break;
			}
			diagnostics.scrollAttempts++;
			page.scrollBy(SCROLL_PIXELS, properties.getScrollDelayMs());
		}
		diagnostics.searchResultsSeen = candidates.size();
		diagnostics.keywordSearchSupported = diagnostics.searchMode == InstagramSearchMode.KEYWORD && !candidates.isEmpty();
		return List.copyOf(candidates.values());
	}

	private List<ScrapedPostDTO> collectPosts(
			BrowserPage searchPage,
			ScraperExecutionContext context,
			List<InstagramPostCandidate> candidates,
			InstagramScrapeDiagnostics diagnostics,
			long deadlineNanos
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		String searchUrl = searchPage.url();
		int tooOldAtStart = diagnostics.tooOldSkipped;
		int targetMaxPosts = effectiveMaxPosts(context);
		for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
			if (posts.size() >= targetMaxPosts || isRunTimedOut(deadlineNanos, diagnostics)) {
				break;
			}
			InstagramPostCandidate candidate = candidates.get(candidateIndex);
			BrowserPage articlePage = searchPage;
			try {
				diagnostics.postsOpened++;
				openCandidateFromSearchPage(searchPage, candidate, searchUrl);
				articlePage.waitForSelector(InstagramSelectors.POST_ROOT, properties.getPostOpenTimeoutMs());
				articlePage.waitForTimeout(properties.getActionDelayMs());
				Document document = Jsoup.parse(articlePage.content(), candidate.postUrl());
				InstagramAuthenticationStatus pageStatus = InstagramAuthenticationPageInspector.inspect(articlePage.url(), document.html());
				if (isHardFailure(pageStatus)) {
					diagnostics.authenticationStatus = pageStatus;
					incrementAuthenticationDiagnostic(pageStatus, diagnostics);
					diagnostics.authenticationExpiredDuringRun = true;
					break;
				}
				InstagramScraperSupport.extractPost(document, context, candidate, dateParser, diagnostics, properties)
						.map(post -> withDiagnostics(post, diagnostics))
						.ifPresent(post -> addIfInRange(post, posts, context, diagnostics));
				if (posts.isEmpty()
						&& diagnostics.tooOldSkipped - tooOldAtStart >= targetMaxPosts) {
					log.info(
							"Stopping Instagram candidate checks after consecutive out-of-range older posts: keyword={}, checked={}, tooOld={}",
							context.keyword().getWord(),
							candidateIndex + 1,
							diagnostics.tooOldSkipped - tooOldAtStart
					);
					break;
				}
			} catch (BrowserEngineException exception) {
				diagnostics.postLoadFailures++;
				diagnostics.extractionFailures++;
				log.warn(
						"Instagram post load failed: postUrl={}, message={}, pageSummary={}",
						candidate.postUrl(),
						exception.getMessage(),
						pageSummary(articlePage)
				);
			} finally {
				returnToSearchPage(searchPage, searchUrl);
			}
		}
		diagnostics.postsCollected = posts.size();
		return posts;
	}

	private void openCandidateFromSearchPage(BrowserPage searchPage, InstagramPostCandidate candidate, String searchUrl) {
		searchPage.navigate(searchUrl);
		searchPage.waitForSelector(InstagramSelectors.BODY, properties.getTimelineLoadTimeoutMs());
		searchPage.waitForTimeout(properties.getActionDelayMs());
		String href = relativePostHref(candidate.postUrl());
		if (!clickSearchResultLink(searchPage, href, candidate.postUrl())) {
			throw new BrowserEngineException("Instagram search result link was not found for " + candidate.postUrl());
		}
		searchPage.waitForTimeout(properties.getActionDelayMs());
	}

	private boolean clickSearchResultLink(BrowserPage searchPage, String href, String postUrl) {
		int maxAttempts = Math.min(properties.getMaxScrollAttempts(), properties.getMaxPostLinkScrollAttempts());
		for (int attempt = 0; attempt <= maxAttempts; attempt++) {
			if (searchPage.clickLinkByHref(href) || searchPage.clickLinkByHref(postUrl)) {
				return true;
			}
			if (attempt < maxAttempts) {
				searchPage.scrollBy(SCROLL_PIXELS, properties.getScrollDelayMs());
			}
		}
		return false;
	}

	private int effectiveMaxPosts(ScraperExecutionContext context) {
		return Math.min(context.maxPosts(), properties.getMaxPostsPerRun());
	}

	private boolean isRunTimedOut(long deadlineNanos, InstagramScrapeDiagnostics diagnostics) {
		if (System.nanoTime() < deadlineNanos) {
			return false;
		}
		if (!diagnostics.timedOut) {
			diagnostics.timedOut = true;
			log.warn("Instagram scraping run timed out after {}ms", properties.getMaxRunDurationMs());
		}
		return true;
	}

	private void returnToSearchPage(BrowserPage searchPage, String searchUrl) {
		try {
			searchPage.goBack();
			searchPage.waitForSelector(InstagramSelectors.BODY, properties.getTimelineLoadTimeoutMs());
			searchPage.waitForTimeout(properties.getActionDelayMs());
		} catch (BrowserEngineException exception) {
			log.warn("Instagram search page back navigation failed; reopening search page: {}", exception.getMessage());
			try {
				searchPage.navigate(searchUrl);
				searchPage.waitForSelector(InstagramSelectors.BODY, properties.getTimelineLoadTimeoutMs());
				searchPage.waitForTimeout(properties.getActionDelayMs());
			} catch (BrowserEngineException reopenException) {
				log.warn("Instagram search page reopen failed after post attempt: {}", reopenException.getMessage());
			}
		}
	}

	private String pageSummary(BrowserPage page) {
		if (page == null) {
			return "<no-page>";
		}
		try {
			Document document = Jsoup.parse(page.content(), page.url());
			String text = document.text();
			if (text.length() > 300) {
				text = text.substring(0, 300);
			}
			return "url=%s,title=%s,main=%d,roleMain=%d,article=%d,time=%d,links=%d,text=%s".formatted(
					page.url(),
					document.title(),
					document.select("main").size(),
					document.select("[role='main']").size(),
					document.select("article").size(),
					document.select("time").size(),
					document.select("a[href]").size(),
					text
			);
		} catch (BrowserEngineException exception) {
			return "<unavailable: " + exception.getMessage() + ">";
		}
	}

	private String relativePostHref(String postUrl) {
		try {
			URI uri = new URI(postUrl);
			String path = uri.getPath();
			return path == null || path.isBlank() ? postUrl : path;
		} catch (URISyntaxException exception) {
			return postUrl;
		}
	}

	private void addIfInRange(
			ScrapedPostDTO post,
			List<ScrapedPostDTO> posts,
			ScraperExecutionContext context,
			InstagramScrapeDiagnostics diagnostics
	) {
		if (DateRangeValidator.isAfterRange(post.postDate(), context.dateTo())) {
			diagnostics.tooNewSkipped++;
			return;
		}
		if (DateRangeValidator.isBeforeRange(post.postDate(), context.dateFrom())) {
			diagnostics.tooOldSkipped++;
			return;
		}
		posts.add(post);
	}

	private ScrapedPostDTO withDiagnostics(ScrapedPostDTO post, InstagramScrapeDiagnostics diagnostics) {
		Map<String, Object> metadata = new LinkedHashMap<>(post.metadata());
		metadata.put("diagnostics", diagnostics.toMetadata());
		return new ScrapedPostDTO(
				post.externalPostId(),
				post.postUrl(),
				post.postDate(),
				post.author(),
				post.text(),
				post.language(),
				post.media(),
				metadata
		);
	}

	private ScraperExecutionResult emptyOrGridFailure(BrowserPage page, InstagramScrapeDiagnostics diagnostics) {
		String html = page.content();
		String text = Jsoup.parse(html).text().toLowerCase();
		if (text.contains("no results") || text.contains("no posts yet") || diagnostics.reelsSkipped > 0) {
			return ScraperExecutionResult.empty();
		}
		return failed("INSTAGRAM_POST_GRID_NOT_FOUND: Instagram search page did not expose post grid links", diagnostics);
	}

	private String homeUrl() {
		String baseUrl = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
				? InstagramScraperSupport.BASE_URL
				: properties.getBaseUrl().trim();
		return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
	}

	private boolean isHardFailure(InstagramAuthenticationStatus status) {
		return status == InstagramAuthenticationStatus.AUTH_REQUIRED
				|| status == InstagramAuthenticationStatus.AUTH_STATE_MISSING
				|| status == InstagramAuthenticationStatus.AUTH_STATE_EXPIRED
				|| status == InstagramAuthenticationStatus.CHALLENGE_REQUIRED
				|| status == InstagramAuthenticationStatus.TWO_FACTOR_REQUIRED
				|| status == InstagramAuthenticationStatus.CONSENT_REQUIRED
				|| status == InstagramAuthenticationStatus.RATE_LIMITED
				|| status == InstagramAuthenticationStatus.ACCOUNT_RESTRICTED
				|| status == InstagramAuthenticationStatus.TEMPORARILY_BLOCKED
				|| status == InstagramAuthenticationStatus.SUSPICIOUS_LOGIN;
	}

	private void incrementAuthenticationDiagnostic(InstagramAuthenticationStatus status, InstagramScrapeDiagnostics diagnostics) {
		switch (status) {
			case CHALLENGE_REQUIRED, TWO_FACTOR_REQUIRED, SUSPICIOUS_LOGIN, CONSENT_REQUIRED -> diagnostics.challengeDetected = true;
			case RATE_LIMITED, TEMPORARILY_BLOCKED -> diagnostics.rateLimitDetected = true;
			default -> {
			}
		}
	}

	private String authenticationFailureMessage(InstagramAuthenticationStatus status) {
		return switch (status) {
			case AUTH_STATE_MISSING ->
					"INSTAGRAM_AUTH_STATE_MISSING: Instagram authentication state is not configured. Generate it with instagramAuthStateInteractive.";
			case AUTH_REQUIRED, AUTH_STATE_EXPIRED ->
					"INSTAGRAM_AUTH_STATE_EXPIRED: Instagram authentication state is missing or expired. Regenerate it with instagramAuthStateInteractive.";
			case CHALLENGE_REQUIRED ->
					"INSTAGRAM_CHALLENGE_REQUIRED: Complete the Instagram challenge manually and regenerate authentication state.";
			case TWO_FACTOR_REQUIRED ->
					"INSTAGRAM_TWO_FACTOR_REQUIRED: Complete Instagram two-factor verification manually and regenerate authentication state.";
			case CONSENT_REQUIRED ->
					"INSTAGRAM_CONSENT_REQUIRED: Complete Instagram consent or optional login dialog manually and regenerate authentication state.";
			case RATE_LIMITED ->
					"INSTAGRAM_RATE_LIMITED: Instagram temporarily limited authenticated access. Retry later.";
			case TEMPORARILY_BLOCKED ->
					"INSTAGRAM_TEMPORARILY_BLOCKED: Instagram temporarily blocked authenticated access. Retry later.";
			case ACCOUNT_RESTRICTED ->
					"INSTAGRAM_ACCOUNT_RESTRICTED: The authenticated Instagram account is restricted.";
			case SUSPICIOUS_LOGIN ->
					"INSTAGRAM_SUSPICIOUS_LOGIN: Instagram requires suspicious-login verification. Complete it manually and regenerate authentication state.";
			case UNKNOWN ->
					"INSTAGRAM_AUTH_STATE_UNKNOWN: Instagram authentication state could not be verified.";
			case AUTHENTICATED -> "INSTAGRAM_AUTHENTICATED";
		};
	}

	private ScraperExecutionResult failed(String message, InstagramScrapeDiagnostics diagnostics) {
		return new ScraperExecutionResult(
				ScraperExecutionStatus.FAILED,
				List.of(),
				0,
				0,
				message + " diagnostics=" + diagnostics.toMetadata(),
				null,
				null
		);
	}
}
