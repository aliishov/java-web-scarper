package org.raul.javawebscarper.scraper.adapter.threads;

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
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThreadsScraperAdapter implements ScraperAdapter {

	private static final int SCROLL_PIXELS = 1_100;

	private final BrowserSessionFactory browserSessionFactory;
	private final ThreadsProperties properties;
	private final ThreadsSearchQueryBuilder searchQueryBuilder;
	private final ThreadsDateParser dateParser;

	@Override
	public String sourceCode() {
		return ThreadsScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return ThreadsScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		if (!properties.isEnabled()) {
			return ScraperExecutionResult.failed("THREADS_SCRAPER_DISABLED: Threads scraper is disabled");
		}
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.isBlank()) {
			return ScraperExecutionResult.failed("THREADS_BAD_REQUEST: search keyword must not be blank");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		ThreadsScrapeDiagnostics diagnostics = new ThreadsScrapeDiagnostics();
		BrowserSessionOptions sessionOptions;
		try {
			sessionOptions = sessionOptions(diagnostics);
		} catch (BrowserEngineException exception) {
			return failed("THREADS_AUTH_STATE_MISSING: " + exception.getMessage(), diagnostics);
		}

		String searchUrl = searchQueryBuilder.buildSearchUrl(properties.getBaseUrl(), keyword);
		log.info(
				"Starting Threads scraping: keyword={}, dateFrom={}, dateTo={}, authStateUsed={}, maxScrollAttempts={}, maxPosts={}",
				keyword,
				context.dateFrom(),
				context.dateTo(),
				diagnostics.authStateUsed,
				properties.getMaxScrollAttempts(),
				context.maxPosts()
		);
		try (BrowserSession session = browserSessionFactory.createSession(sessionOptions)) {
			BrowserPage searchPage = session.newPage();
			searchPage.navigate(searchUrl);
			searchPage.waitForSelector(ThreadsSelectors.BODY, properties.getPageLoadTimeoutMs());
			searchPage.waitForTimeout(properties.getActionDelayMs());
			ThreadsPageStatus initialStatus = ThreadsPageInspector.inspect(searchPage.url(), searchPage.content());
			diagnostics.pageStatus = initialStatus;
			if (isFailure(initialStatus)) {
				return failed(pageFailure(initialStatus), diagnostics);
			}

			List<ThreadsPostCandidate> candidates = collectCandidates(searchPage, context, diagnostics);
			if (candidates.isEmpty()) {
				String pageText = Jsoup.parse(searchPage.content()).text().toLowerCase();
				if (pageText.contains("no results") || pageText.contains("no posts")) {
					return ScraperExecutionResult.empty();
				}
				return failed("THREADS_SEARCH_RESULTS_NOT_FOUND: search page exposed no canonical post links", diagnostics);
			}
			List<ScrapedPostDTO> posts = collectPosts(session, context, candidates, diagnostics);
			if (!posts.isEmpty()) {
				return ScraperExecutionResult.success(posts);
			}
			if (diagnostics.postsOpened > 0 && diagnostics.extractionFailures > 0) {
				return failed("THREADS_EXTRACTION_FAILED: candidates were found but no valid posts were extracted", diagnostics);
			}
			return ScraperExecutionResult.empty();
		} catch (BrowserEngineException exception) {
			log.warn("Threads scraping browser failure: {}", exception.getMessage());
			return failed("THREADS_PAGE_LOAD_FAILED: " + exception.getMessage(), diagnostics);
		} catch (RuntimeException exception) {
			log.error("Unexpected Threads scraping failure", exception);
			return failed("THREADS_EXTRACTION_FAILED: " + exception.getMessage(), diagnostics);
		}
	}

	private BrowserSessionOptions sessionOptions(ThreadsScrapeDiagnostics diagnostics) {
		String configuredPath = properties.getAuthStatePath();
		if (configuredPath == null || configuredPath.isBlank()) {
			if (properties.isAuthenticationRequired()) {
				throw new BrowserEngineException("Threads authentication state is not configured");
			}
			return new BrowserSessionOptions(
					null,
					properties.getLocale(),
					properties.getTimezoneId(),
					null,
					Map.of()
			);
		}
		Path path = Path.of(configuredPath.trim()).toAbsolutePath().normalize();
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			if (properties.isAuthenticationRequired()) {
				throw new BrowserEngineException("Threads authentication state file is missing or unreadable");
			}
			log.warn("Configured Threads auth state is unusable; continuing with anonymous access");
			return BrowserSessionOptions.defaults();
		}
		diagnostics.authStateUsed = true;
		return new BrowserSessionOptions(path, properties.getLocale(), properties.getTimezoneId(), null, Map.of());
	}

	private List<ThreadsPostCandidate> collectCandidates(
			BrowserPage page,
			ScraperExecutionContext context,
			ThreadsScrapeDiagnostics diagnostics
	) {
		Map<String, ThreadsPostCandidate> candidates = new LinkedHashMap<>();
		int maxCandidates = Math.min(properties.getMaxCandidates(), Math.max(context.maxPosts() * 3, context.maxPosts()));
		int maxAttempts = Math.min(properties.getMaxScrollAttempts(), context.maxPages() * properties.getMaxScrollAttempts());
		for (int attempt = 0;
				attempt < maxAttempts && candidates.size() < maxCandidates
						&& diagnostics.noNewIterations < properties.getNoNewPostLimit();
				attempt++) {
			Document document = Jsoup.parse(page.content(), page.url());
			ThreadsPageStatus status = ThreadsPageInspector.inspect(page.url(), document.html());
			diagnostics.pageStatus = status;
			if (isFailure(status)) {
				break;
			}
			int before = candidates.size();
			for (ThreadsPostCandidate candidate : ThreadsScraperSupport.discoverCandidates(
					document,
					maxCandidates,
					diagnostics
			)) {
				if (candidates.putIfAbsent(candidate.externalPostId(), candidate) != null) {
					diagnostics.duplicateCandidates++;
				}
			}
			diagnostics.uniqueCandidates = candidates.size();
			diagnostics.noNewIterations = candidates.size() == before ? diagnostics.noNewIterations + 1 : 0;
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
			ScraperExecutionContext context,
			List<ThreadsPostCandidate> candidates,
			ThreadsScrapeDiagnostics diagnostics
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		for (ThreadsPostCandidate candidate : candidates) {
			if (posts.size() >= context.maxPosts()) {
				break;
			}
			try (BrowserPage postPage = session.openNewPage()) {
				diagnostics.postsOpened++;
				postPage.navigate(candidate.postUrl());
				postPage.waitForSelector(ThreadsSelectors.BODY, properties.getPageLoadTimeoutMs());
				postPage.waitForTimeout(properties.getActionDelayMs());
				Document document = Jsoup.parse(postPage.content(), candidate.postUrl());
				ThreadsPageStatus status = ThreadsPageInspector.inspect(postPage.url(), document.html());
				diagnostics.pageStatus = status;
				if (isFailure(status)) {
					break;
				}
				ThreadsScraperSupport.extractPost(
						document,
						context,
						candidate,
						dateParser,
						properties,
						diagnostics
				).ifPresent(post -> addIfInRange(post, posts, context, diagnostics));
			} catch (BrowserEngineException exception) {
				diagnostics.postLoadFailures++;
				log.warn("Threads post load failed: postUrl={}, message={}", candidate.postUrl(), exception.getMessage());
			}
		}
		diagnostics.postsCollected = posts.size();
		return posts;
	}

	private void addIfInRange(
			ScrapedPostDTO post,
			List<ScrapedPostDTO> posts,
			ScraperExecutionContext context,
			ThreadsScrapeDiagnostics diagnostics
	) {
		if (DateRangeValidator.isAfterRange(post.postDate(), context.dateTo())) {
			diagnostics.tooNewSkipped++;
		} else if (DateRangeValidator.isBeforeRange(post.postDate(), context.dateFrom())) {
			diagnostics.tooOldSkipped++;
		} else {
			posts.add(post);
		}
	}

	private boolean isFailure(ThreadsPageStatus status) {
		return status == ThreadsPageStatus.LOGIN_REQUIRED
				|| status == ThreadsPageStatus.CHALLENGE_REQUIRED
				|| status == ThreadsPageStatus.RATE_LIMITED;
	}

	private String pageFailure(ThreadsPageStatus status) {
		return switch (status) {
			case LOGIN_REQUIRED -> "THREADS_LOGIN_REQUIRED: configure a valid Threads storage state or retry anonymous browsing";
			case CHALLENGE_REQUIRED -> "THREADS_CHALLENGE_REQUIRED: complete the Threads challenge manually; bypass is not attempted";
			case RATE_LIMITED -> "THREADS_RATE_LIMITED: Threads temporarily limited access; retry later";
			default -> "THREADS_PAGE_NOT_READY: Threads page was not ready";
		};
	}

	private ScraperExecutionResult failed(String message, ThreadsScrapeDiagnostics diagnostics) {
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
