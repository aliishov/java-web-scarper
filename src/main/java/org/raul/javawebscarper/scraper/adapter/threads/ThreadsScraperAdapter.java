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
import org.raul.javawebscarper.scraper.support.DateRangeValidator;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

		BrowserSessionOptions sessionOptions;
		try {
			sessionOptions = sessionOptions();
		} catch (BrowserEngineException exception) {
			return ScraperExecutionResult.failed("THREADS_AUTH_STATE_MISSING: " + exception.getMessage());
		}

		String searchUrl = searchUrl(keyword.trim());
		log.info(
				"Starting Threads scraping: keyword={}, dateFrom={}, dateTo={}, maxPages={}, maxPosts={}",
				keyword,
				context.dateFrom(),
				context.dateTo(),
				context.maxPages(),
				context.maxPosts()
		);

		try (BrowserSession session = browserSessionFactory.createSession(sessionOptions)) {
			BrowserPage searchPage = session.newPage();
			searchPage.navigate(searchUrl);
			searchPage.waitForSelector(ThreadsSelectors.BODY, properties.getTimelineLoadTimeoutMs());
			searchPage.waitForTimeout(properties.getActionDelayMs());
			if (isAuthenticationWall(searchPage)) {
				return ScraperExecutionResult.failed(
						"THREADS_AUTH_STATE_EXPIRED: Threads authentication state is missing or expired. "
								+ "Regenerate it with threadsAuthStateInteractive."
				);
			}

			List<ThreadsPostCandidate> candidates = collectCandidates(searchPage, context);
			if (candidates.isEmpty()) {
				String pageText = Jsoup.parse(searchPage.content()).text().toLowerCase();
				if (pageText.contains("no results") || pageText.contains("couldn't find")) {
					return ScraperExecutionResult.empty();
				}
				return ScraperExecutionResult.failed(
						"THREADS_TIMELINE_NOT_FOUND: Threads search page did not expose post links"
				);
			}
			List<ScrapedPostDTO> posts = collectPosts(session, context, candidates);
			log.info(
					"Finished Threads scraping: keyword={}, candidates={}, postsCollected={}",
					keyword,
					candidates.size(),
					posts.size()
			);
			return posts.isEmpty() ? ScraperExecutionResult.empty() : ScraperExecutionResult.success(posts);
		} catch (BrowserEngineException exception) {
			log.warn("Threads scraping failed: {}", exception.getMessage());
			return ScraperExecutionResult.failed("THREADS_LOAD_FAILED: " + exception.getMessage());
		} catch (RuntimeException exception) {
			log.error("Unexpected Threads scraping failure", exception);
			return ScraperExecutionResult.failed("THREADS_EXTRACTION_FAILED: " + exception.getMessage());
		}
	}

	private BrowserSessionOptions sessionOptions() {
		String configuredPath = properties.getAuthStatePath();
		if (configuredPath == null || configuredPath.isBlank()) {
			if (properties.isAuthenticationRequired()) {
				throw new BrowserEngineException(
						"Threads authentication state is not configured. Generate it with threadsAuthStateInteractive."
				);
			}
			return BrowserSessionOptions.defaults();
		}
		Path path = Path.of(configuredPath.trim()).toAbsolutePath().normalize();
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			throw new BrowserEngineException("Threads authentication state file is missing or unreadable");
		}
		return new BrowserSessionOptions(path, properties.getLocale(), properties.getTimezoneId(), null, Map.of());
	}

	private List<ThreadsPostCandidate> collectCandidates(BrowserPage page, ScraperExecutionContext context) {
		Map<String, ThreadsPostCandidate> candidates = new LinkedHashMap<>();
		int maxCandidates = Math.min(
				properties.getMaxCandidates(),
				Math.max(context.maxPosts() * 3, context.maxPosts())
		);
		int noNewIterations = 0;
		int attempts = Math.min(properties.getMaxScrollAttempts(), context.maxPages() * properties.getMaxScrollAttempts());
		for (int attempt = 0;
			attempt < attempts && candidates.size() < maxCandidates && noNewIterations < properties.getNoNewPostLimit();
			attempt++) {
			Document document = Jsoup.parse(page.content(), page.url());
			int before = candidates.size();
			for (ThreadsPostCandidate candidate : ThreadsScraperSupport.discoverCandidates(document, maxCandidates)) {
				candidates.putIfAbsent(candidate.externalPostId(), candidate);
			}
			noNewIterations = candidates.size() == before ? noNewIterations + 1 : 0;
			if (candidates.size() < maxCandidates) {
				page.scrollBy(SCROLL_PIXELS, properties.getScrollDelayMs());
			}
		}
		return List.copyOf(candidates.values());
	}

	private List<ScrapedPostDTO> collectPosts(
			BrowserSession session,
			ScraperExecutionContext context,
			List<ThreadsPostCandidate> candidates
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		for (ThreadsPostCandidate candidate : candidates) {
			if (posts.size() >= context.maxPosts()) {
				break;
			}
			try (BrowserPage page = session.openNewPage()) {
				page.navigate(candidate.postUrl());
				page.waitForSelector(ThreadsSelectors.BODY, properties.getPostOpenTimeoutMs());
				page.waitForTimeout(properties.getActionDelayMs());
				Document document = Jsoup.parse(page.content(), candidate.postUrl());
				ThreadsScraperSupport.extractPost(document, context, candidate, dateParser)
						.filter(post -> DateRangeValidator.isInsideRange(
								post.postDate(),
								context.dateFrom(),
								context.dateTo()
						))
						.ifPresent(posts::add);
			} catch (BrowserEngineException exception) {
				log.warn("Threads post load failed: postUrl={}, error={}", candidate.postUrl(), exception.getMessage());
			}
		}
		return posts;
	}

	private boolean isAuthenticationWall(BrowserPage page) {
		String url = page.url() == null ? "" : page.url().toLowerCase();
		String text = Jsoup.parse(page.content()).text().toLowerCase();
		return url.contains("/login")
				|| text.contains("log in with your instagram account")
				|| text.contains("log in or sign up for threads");
	}

	private String searchUrl(String keyword) {
		String baseUrl = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
				? ThreadsScraperSupport.BASE_URL
				: properties.getBaseUrl().trim();
		baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		return baseUrl + "/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
				+ "&serp_type=default";
	}
}
