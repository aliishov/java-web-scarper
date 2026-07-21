package org.raul.javawebscarper.scraper.adapter.tiktok;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TikTokScraperAdapter implements ScraperAdapter {

	private static final int MIN_KEYWORD_LENGTH = 1;
	private static final int SCROLL_PIXELS = 1_100;

	private final BrowserSessionFactory browserSessionFactory;
	private final TikTokProperties properties;
	private final TikTokSearchQueryBuilder searchQueryBuilder;
	private final TikTokDateParser dateParser;
	private final TikTokAuthenticationVerifier authenticationVerifier;
	private final TikTokPageReadinessVerifier readinessVerifier;

	@Override
	public String sourceCode() {
		return TikTokScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return TikTokScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		if (!properties.isEnabled()) {
			return ScraperExecutionResult.failed("TIKTOK_SCRAPER_DISABLED: TikTok scraper is disabled");
		}
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("TIKTOK_BAD_REQUEST: search keyword must not be blank");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());

		TikTokScrapeDiagnostics diagnostics = new TikTokScrapeDiagnostics();
		diagnostics.searchMode = searchQueryBuilder.resolveMode(keyword.trim(), properties.getSearchMode());
		diagnostics.searchQuery = searchQueryBuilder.buildSearchUrl(properties.getBaseUrl(), keyword.trim(), properties.getSearchMode());

		BrowserSessionOptions sessionOptions;
		try {
			sessionOptions = sessionOptions(diagnostics);
		} catch (BrowserEngineException exception) {
			log.warn("TikTok auth state is not usable: {}", exception.getMessage());
			diagnostics.authenticationStatus = TikTokAuthenticationStatus.AUTH_STATE_MISSING;
			return failed(authenticationFailureMessage(TikTokAuthenticationStatus.AUTH_STATE_MISSING), diagnostics);
		}

		log.info(
				"Starting TikTok scraping: keyword={}, dateFrom={}, dateTo={}, mode={}, authStateUsed={}, maxScrollAttempts={}, maxPosts={}",
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
			if (properties.isAuthenticationRequired() || diagnostics.authStateUsed) {
				TikTokAuthenticationStatus authenticationStatus = verifySession(searchPage);
				diagnostics.authenticationStatus = authenticationStatus;
				updateAuthenticationDiagnostics(authenticationStatus, diagnostics);
				if (isHardAuthenticationFailure(authenticationStatus)) {
					return failed(authenticationFailureMessage(authenticationStatus), diagnostics);
				}
			}

			openSearchPage(searchPage, keyword.trim(), diagnostics);
			TikTokPageReadinessStatus searchReadiness = readinessVerifier.verify(searchPage);
			updateReadinessDiagnostics(searchReadiness, diagnostics);
			if (searchReadiness == TikTokPageReadinessStatus.COOKIE_CONSENT) {
				dismissCookieConsent(searchPage, diagnostics);
				searchReadiness = readinessVerifier.verify(searchPage);
				updateReadinessDiagnostics(searchReadiness, diagnostics);
			}
			if (isHardReadinessFailure(searchReadiness)) {
				return failed(readinessFailureMessage(searchReadiness), diagnostics);
			}
			TikTokAuthenticationStatus searchAuthenticationStatus = authenticationVerifier.verify(searchPage);
			diagnostics.authenticationStatus = searchAuthenticationStatus;
			updateAuthenticationDiagnostics(searchAuthenticationStatus, diagnostics);
			if (isHardAuthenticationFailure(searchAuthenticationStatus)) {
				return failed(authenticationFailureMessage(searchAuthenticationStatus), diagnostics);
			}

			confirmVideosTab(searchPage, diagnostics);
			List<TikTokPostCandidate> candidates = collectCandidates(searchPage, context, diagnostics);
			if (isHardReadinessFailure(diagnostics.pageReadinessStatus)) {
				return failed(readinessFailureMessage(diagnostics.pageReadinessStatus), diagnostics);
			}
			if (isHardAuthenticationFailure(diagnostics.authenticationStatus)) {
				return failed(authenticationFailureMessage(diagnostics.authenticationStatus), diagnostics);
			}
			if (candidates.isEmpty()) {
				return emptyOrSearchFailure(searchPage, diagnostics);
			}
			List<ScrapedPostDTO> posts = collectPosts(session, searchPage, context, candidates, diagnostics);

			log.info(
					"Finished TikTok scraping: keyword={}, postsCollected={}, candidates={}, duplicates={}, tooNew={}, tooOld={}, "
							+ "dateFailures={}, postLoadFailures={}, captionEmpty={}, videoUrlUnavailable={}, sponsoredSkipped={}, "
							+ "scrollAttempts={}, noNewIterations={}, captcha={}, verification={}, rateLimit={}",
					keyword,
					posts.size(),
					diagnostics.uniqueCandidates,
					diagnostics.duplicateCandidatesSkipped,
					diagnostics.tooNewSkipped,
					diagnostics.tooOldSkipped,
					diagnostics.dateParseFailures,
					diagnostics.postLoadFailures,
					diagnostics.captionEmpty,
					diagnostics.videoUrlUnavailable,
					diagnostics.sponsoredPostsSkipped,
					diagnostics.scrollAttempts,
					diagnostics.noNewPostIterations,
					diagnostics.captchaDetected,
					diagnostics.verificationDetected,
					diagnostics.rateLimitDetected
			);

			if (!posts.isEmpty()) {
				return ScraperExecutionResult.success(posts);
			}
			if (diagnostics.postsOpened > 0 && diagnostics.postLoadFailures >= diagnostics.postsOpened) {
				return failed("TIKTOK_POST_LOAD_FAILED: candidates were found but post pages could not be loaded", diagnostics);
			}
			if (diagnostics.postsOpened > 0) {
				return failed("TIKTOK_EXTRACTION_FAILED: candidates were found but no valid TikTok posts were extracted", diagnostics);
			}
			return ScraperExecutionResult.empty();
		} catch (TikTokFlowException exception) {
			log.warn("TikTok scraping stopped by blocker state: {}", exception.getMessage());
			return failed(exception.getMessage(), diagnostics);
		} catch (BrowserEngineException exception) {
			log.warn("TikTok scraping failed: {}", exception.getMessage());
			return failed("TIKTOK_POST_LOAD_FAILED: " + exception.getMessage(), diagnostics);
		} catch (RuntimeException exception) {
			log.error("Unexpected TikTok scraping failure", exception);
			return failed("TIKTOK_EXTRACTION_FAILED: " + exception.getMessage(), diagnostics);
		}
	}

	private BrowserSessionOptions sessionOptions(TikTokScrapeDiagnostics diagnostics) {
		String authStatePath = properties.getAuthStatePath();
		if (authStatePath == null || authStatePath.isBlank()) {
			if (properties.isAuthenticationRequired()) {
				throw new BrowserEngineException("TikTok authentication state is not configured");
			}
			return new BrowserSessionOptions(null, properties.getLocale(), properties.getTimezoneId(), null, Map.of());
		}
		Path path = Path.of(authStatePath.trim()).toAbsolutePath().normalize();
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			throw new BrowserEngineException("TikTok authentication state file is missing or unreadable");
		}
		diagnostics.authStateUsed = true;
		return new BrowserSessionOptions(path, properties.getLocale(), properties.getTimezoneId(), null, Map.of());
	}

	private TikTokAuthenticationStatus verifySession(BrowserPage page) {
		page.navigate(homeUrl());
		page.waitForSelector(TikTokSelectors.BODY, properties.getReadinessTimeoutMs());
		page.waitForTimeout(properties.getActionDelayMs());
		return authenticationVerifier.verify(page);
	}

	private void openSearchPage(BrowserPage page, String keyword, TikTokScrapeDiagnostics diagnostics) {
		if (diagnostics.searchMode == TikTokSearchMode.KEYWORD) {
			try {
				page.navigate(homeUrl());
				page.waitForSelector(TikTokSelectors.BODY, properties.getReadinessTimeoutMs());
				page.waitForTimeout(properties.getActionDelayMs());
				TikTokPageReadinessStatus readiness = readinessVerifier.verify(page);
				updateReadinessDiagnostics(readiness, diagnostics);
				if (readiness == TikTokPageReadinessStatus.COOKIE_CONSENT) {
					dismissCookieConsent(page, diagnostics);
					readiness = readinessVerifier.verify(page);
					updateReadinessDiagnostics(readiness, diagnostics);
				}
				if (isHardReadinessFailure(readiness)) {
					throw new TikTokFlowException(readinessFailureMessage(readiness));
				}
				tryUiSearch(page, keyword, diagnostics);
				if (isSearchResultUrl(page.url(), keyword)) {
					return;
				}
				log.warn("TikTok UI search did not navigate to usable results; using direct search URL fallback");
			} catch (TikTokFlowException exception) {
				throw exception;
			} catch (BrowserEngineException exception) {
				log.warn("TikTok UI search failed; using direct search URL fallback: {}", exception.getMessage());
			}
		}
		diagnostics.directSearchFallbackUsed = true;
		page.navigate(diagnostics.searchQuery);
		page.waitForSelector(TikTokSelectors.BODY, properties.getSearchTimeoutMs());
		page.waitForTimeout(properties.getActionDelayMs());
	}

	private void tryUiSearch(BrowserPage page, String keyword, TikTokScrapeDiagnostics diagnostics) {
		diagnostics.searchNavigationUsed = true;
		try {
			page.click(TikTokSelectors.SEARCH_BUTTON);
			page.waitForTimeout(properties.getActionDelayMs());
		} catch (BrowserEngineException exception) {
			log.debug("TikTok search button was not needed or not visible: {}", exception.getMessage());
		}
		page.fill(TikTokSelectors.SEARCH_INPUT, keyword);
		page.press(TikTokSelectors.SEARCH_INPUT, "Enter");
		page.waitForTimeout(properties.getScrollDelayMs());
		if (!isSearchResultUrl(page.url(), keyword)) {
			try {
				page.click(TikTokSelectors.SEARCH_SUBMIT);
				page.waitForTimeout(properties.getScrollDelayMs());
			} catch (BrowserEngineException exception) {
				log.debug("TikTok search submit button was not usable: {}", exception.getMessage());
			}
		}
	}

	private void dismissCookieConsent(BrowserPage page, TikTokScrapeDiagnostics diagnostics) {
		diagnostics.cookieConsentDetected = true;
		for (String selector : List.of(
				"button:has-text('Accept all cookies')",
				"button:has-text('Allow all cookies')",
				"button:has-text('Accept')",
				"button:has-text('Разрешить все')",
				"button:has-text('Принять')",
				"button:has-text('Qəbul et')",
				"button:has-text('Kabul et')"
		)) {
			try {
				page.click(selector);
				page.waitForTimeout(properties.getActionDelayMs());
				log.info("TikTok cookie consent dismissed with selector {}", selector);
				return;
			} catch (BrowserEngineException exception) {
				log.debug("TikTok cookie consent selector was not usable: selector={}, message={}", selector, exception.getMessage());
			}
		}
	}

	private void confirmVideosTab(BrowserPage page, TikTokScrapeDiagnostics diagnostics) {
		if (diagnostics.searchMode == TikTokSearchMode.HASHTAG || isVideosRoute(page.url())) {
			diagnostics.videosTabConfirmed = true;
			return;
		}
		try {
			page.click("a[href*='/search/video'], [role='tab'][href*='/search/video']");
			diagnostics.videosTabFound = true;
			page.waitForTimeout(properties.getActionDelayMs());
			diagnostics.videosTabConfirmed = isVideosRoute(page.url());
		} catch (BrowserEngineException exception) {
			log.info("TikTok Videos tab was not clickable; continuing if canonical video links are visible: {}", exception.getMessage());
		}
	}

	private List<TikTokPostCandidate> collectCandidates(
			BrowserPage page,
			ScraperExecutionContext context,
			TikTokScrapeDiagnostics diagnostics
	) {
		LinkedHashMap<String, TikTokPostCandidate> candidates = new LinkedHashMap<>();
		int maxCandidates = Math.min(properties.getMaxCandidates(), Math.max(context.maxPosts() * 3, context.maxPosts()));
		for (int attempt = 0;
				attempt < Math.min(properties.getMaxScrollAttempts(), context.maxPages() * properties.getMaxScrollAttempts())
						&& candidates.size() < maxCandidates
						&& diagnostics.noNewPostIterations < properties.getNoNewPostLimit();
				attempt++) {
			Document document = Jsoup.parse(page.content(), page.url() == null ? TikTokScraperSupport.BASE_URL : page.url());
			TikTokPageReadinessStatus readiness = TikTokPageReadinessVerifier.inspect(page.url(), document.html());
			updateReadinessDiagnostics(readiness, diagnostics);
			if (isHardReadinessFailure(readiness)) {
				break;
			}
			TikTokAuthenticationStatus authenticationStatus = TikTokAuthenticationVerifier.inspect(page.url(), document.html());
			diagnostics.authenticationStatus = authenticationStatus;
			updateAuthenticationDiagnostics(authenticationStatus, diagnostics);
			if (isHardAuthenticationFailure(authenticationStatus)) {
				break;
			}
			int before = candidates.size();
			for (TikTokPostCandidate candidate : TikTokScraperSupport.discoverPostCandidates(document, maxCandidates, diagnostics)) {
				candidates.putIfAbsent(candidate.externalPostId(), candidate);
			}
			diagnostics.uniqueCandidates = candidates.size();
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
		return List.copyOf(candidates.values());
	}

	private List<ScrapedPostDTO> collectPosts(
			BrowserSession session,
			BrowserPage searchPage,
			ScraperExecutionContext context,
			List<TikTokPostCandidate> candidates,
			TikTokScrapeDiagnostics diagnostics
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		for (TikTokPostCandidate candidate : candidates) {
			if (posts.size() >= context.maxPosts()) {
				break;
			}
			BrowserPage articlePage = null;
			boolean closeArticlePage = false;
			try {
				articlePage = properties.isOpenPostForDetails() ? session.openNewPage() : searchPage;
				closeArticlePage = properties.isOpenPostForDetails();
				diagnostics.postsOpened++;
				articlePage.navigate(candidate.postUrl());
				articlePage.waitForSelector(TikTokSelectors.BODY, properties.getPostOpenTimeoutMs());
				articlePage.waitForTimeout(properties.getActionDelayMs());
				Document document = Jsoup.parse(articlePage.content(), candidate.postUrl());
				TikTokPageReadinessStatus readiness = TikTokPageReadinessVerifier.inspect(articlePage.url(), document.html());
				updateReadinessDiagnostics(readiness, diagnostics);
				if (isHardReadinessFailure(readiness)) {
					break;
				}
				TikTokAuthenticationStatus authenticationStatus = TikTokAuthenticationVerifier.inspect(articlePage.url(), document.html());
				diagnostics.authenticationStatus = authenticationStatus;
				updateAuthenticationDiagnostics(authenticationStatus, diagnostics);
				if (isHardAuthenticationFailure(authenticationStatus)) {
					break;
				}
				TikTokScraperSupport.extractPost(document, context, candidate, dateParser, diagnostics, properties)
						.map(post -> withDiagnostics(post, diagnostics))
						.ifPresent(post -> addIfInRange(post, posts, context, diagnostics));
			} catch (BrowserEngineException exception) {
				diagnostics.postLoadFailures++;
				log.warn("TikTok post load failed: postUrl={}, message={}", candidate.postUrl(), exception.getMessage());
			} finally {
				if (closeArticlePage && articlePage != null) {
					try {
						articlePage.close();
					} catch (BrowserEngineException exception) {
						log.warn("Failed to close TikTok article page cleanly: {}", exception.getMessage());
					}
				}
			}
		}
		diagnostics.postsCollected = posts.size();
		return posts;
	}

	private void addIfInRange(
			ScrapedPostDTO post,
			List<ScrapedPostDTO> posts,
			ScraperExecutionContext context,
			TikTokScrapeDiagnostics diagnostics
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

	private ScrapedPostDTO withDiagnostics(ScrapedPostDTO post, TikTokScrapeDiagnostics diagnostics) {
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

	private ScraperExecutionResult emptyOrSearchFailure(BrowserPage page, TikTokScrapeDiagnostics diagnostics) {
		String text = Jsoup.parse(page.content()).text().toLowerCase(Locale.ROOT);
		if (text.contains("no results") || text.contains("couldn't find") || text.contains("no videos") || text.contains("нет результатов")) {
			return ScraperExecutionResult.empty();
		}
		return failed("TIKTOK_RESULTS_NOT_FOUND: TikTok search did not expose canonical video links", diagnostics);
	}

	private String homeUrl() {
		String baseUrl = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
				? TikTokScraperSupport.BASE_URL
				: properties.getBaseUrl().trim();
		return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
	}

	private boolean isSearchResultUrl(String url, String keyword) {
		if (url == null || url.isBlank()) {
			return false;
		}
		String lower = url.toLowerCase(Locale.ROOT);
		return lower.contains("/search") || lower.contains("/tag/");
	}

	private boolean isVideosRoute(String url) {
		return url != null && url.toLowerCase(Locale.ROOT).contains("/search/video");
	}

	private boolean isHardAuthenticationFailure(TikTokAuthenticationStatus status) {
		return status == TikTokAuthenticationStatus.AUTH_REQUIRED
				|| status == TikTokAuthenticationStatus.AUTH_STATE_MISSING
				|| status == TikTokAuthenticationStatus.AUTH_STATE_EXPIRED
				|| status == TikTokAuthenticationStatus.LOGIN_MODAL_BLOCKING
				|| status == TikTokAuthenticationStatus.CAPTCHA_REQUIRED
				|| status == TikTokAuthenticationStatus.VERIFICATION_REQUIRED
				|| status == TikTokAuthenticationStatus.TWO_FACTOR_REQUIRED
				|| status == TikTokAuthenticationStatus.RATE_LIMITED
				|| status == TikTokAuthenticationStatus.TEMPORARILY_BLOCKED
				|| status == TikTokAuthenticationStatus.ACCOUNT_RESTRICTED
				|| status == TikTokAuthenticationStatus.CONSENT_REQUIRED;
	}

	private boolean isHardReadinessFailure(TikTokPageReadinessStatus status) {
		return status == TikTokPageReadinessStatus.LOGIN_MODAL
				|| status == TikTokPageReadinessStatus.CAPTCHA
				|| status == TikTokPageReadinessStatus.VERIFICATION
				|| status == TikTokPageReadinessStatus.RATE_LIMITED
				|| status == TikTokPageReadinessStatus.ERROR_PAGE;
	}

	private void updateAuthenticationDiagnostics(TikTokAuthenticationStatus status, TikTokScrapeDiagnostics diagnostics) {
		if (status == null) {
			return;
		}
		if (status == TikTokAuthenticationStatus.ANONYMOUS_ACCESS) {
			diagnostics.anonymousSession = true;
		}
		switch (status) {
			case AUTH_REQUIRED, AUTH_STATE_EXPIRED, LOGIN_MODAL_BLOCKING -> diagnostics.loginModalDetected = true;
			case CAPTCHA_REQUIRED -> diagnostics.captchaDetected = true;
			case VERIFICATION_REQUIRED, TWO_FACTOR_REQUIRED -> diagnostics.verificationDetected = true;
			case RATE_LIMITED, TEMPORARILY_BLOCKED -> diagnostics.rateLimitDetected = true;
			default -> {
			}
		}
	}

	private void updateReadinessDiagnostics(TikTokPageReadinessStatus status, TikTokScrapeDiagnostics diagnostics) {
		diagnostics.pageReadinessStatus = status;
		diagnostics.readinessAttempts++;
		if (status == TikTokPageReadinessStatus.COOKIE_CONSENT) {
			diagnostics.cookieConsentDetected = true;
		}
		if (status == TikTokPageReadinessStatus.LOGIN_MODAL) {
			diagnostics.loginModalDetected = true;
		}
		if (status == TikTokPageReadinessStatus.CAPTCHA) {
			diagnostics.captchaDetected = true;
		}
		if (status == TikTokPageReadinessStatus.VERIFICATION) {
			diagnostics.verificationDetected = true;
		}
		if (status == TikTokPageReadinessStatus.RATE_LIMITED) {
			diagnostics.rateLimitDetected = true;
		}
	}

	private String authenticationFailureMessage(TikTokAuthenticationStatus status) {
		return switch (status) {
			case AUTH_STATE_MISSING ->
					"TIKTOK_AUTH_STATE_MISSING: TikTok authentication state is not configured. Set TIKTOK_AUTH_STATE_PATH or disable authentication-required.";
			case AUTH_REQUIRED, AUTH_STATE_EXPIRED ->
					"TIKTOK_LOGIN_REQUIRED: TikTok requires login for this page/session. Authenticate manually and provide Playwright storage state.";
			case LOGIN_MODAL_BLOCKING ->
					"TIKTOK_LOGIN_MODAL_BLOCKING: TikTok showed a blocking login modal.";
			case CAPTCHA_REQUIRED ->
					"TIKTOK_CAPTCHA_REQUIRED: TikTok requires CAPTCHA. Complete it manually in a browser session; automated bypass is not supported.";
			case VERIFICATION_REQUIRED ->
					"TIKTOK_VERIFICATION_REQUIRED: TikTok requires account or security verification. Complete it manually.";
			case TWO_FACTOR_REQUIRED ->
					"TIKTOK_VERIFICATION_REQUIRED: TikTok requires two-factor verification. Complete it manually.";
			case RATE_LIMITED ->
					"TIKTOK_RATE_LIMITED: TikTok temporarily rate-limited this session. Retry later.";
			case TEMPORARILY_BLOCKED ->
					"TIKTOK_TEMPORARILY_BLOCKED: TikTok temporarily blocked this session. Retry later.";
			case ACCOUNT_RESTRICTED ->
					"TIKTOK_ACCOUNT_RESTRICTED: The TikTok account/session is restricted.";
			case CONSENT_REQUIRED ->
					"TIKTOK_VERIFICATION_REQUIRED: TikTok requires a consent or optional account dialog before scraping can continue.";
			case UNKNOWN ->
					"TIKTOK_AUTH_STATE_EXPIRED: TikTok authentication state could not be verified.";
			case AUTHENTICATED, ANONYMOUS_ACCESS -> "TIKTOK_AUTHENTICATION_OK";
		};
	}

	private String readinessFailureMessage(TikTokPageReadinessStatus status) {
		return switch (status) {
			case LOGIN_MODAL -> "TIKTOK_LOGIN_REQUIRED: TikTok showed a login wall.";
			case CAPTCHA -> "TIKTOK_CAPTCHA_REQUIRED: TikTok showed CAPTCHA.";
			case VERIFICATION -> "TIKTOK_VERIFICATION_REQUIRED: TikTok showed verification.";
			case RATE_LIMITED -> "TIKTOK_RATE_LIMITED: TikTok rate-limited this session.";
			case ERROR_PAGE -> "TIKTOK_PAGE_NOT_READY: TikTok returned an unavailable/error page.";
			case LOADING, UNKNOWN -> "TIKTOK_PAGE_NOT_READY: TikTok page did not reach a usable state.";
			case COOKIE_CONSENT, READY -> "TIKTOK_PAGE_READY";
		};
	}

	private ScraperExecutionResult failed(String message, TikTokScrapeDiagnostics diagnostics) {
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

	private static class TikTokFlowException extends RuntimeException {

		TikTokFlowException(String message) {
			super(message);
		}
	}
}
