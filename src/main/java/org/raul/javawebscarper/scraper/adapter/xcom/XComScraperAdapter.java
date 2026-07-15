package org.raul.javawebscarper.scraper.adapter.xcom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.raul.javawebscarper.browser.BrowserEngineException;
import org.raul.javawebscarper.browser.BrowserPage;
import org.raul.javawebscarper.browser.BrowserSession;
import org.raul.javawebscarper.browser.BrowserSessionFactory;
import org.raul.javawebscarper.browser.BrowserSessionOptions;
import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.scraper.adapter.ScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionStatus;
import org.raul.javawebscarper.scraper.support.DateRangeValidator;
import org.raul.javawebscarper.scraper.support.UrlNormalizer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class XComScraperAdapter implements ScraperAdapter {

	private static final int MIN_KEYWORD_LENGTH = 1;
	private static final int SCROLL_PIXELS = 1_100;
	private static final int MIN_TEXT_LENGTH = 2;

	private final BrowserSessionFactory browserSessionFactory;
	private final XComProperties properties;
	private final XComSearchQueryBuilder queryBuilder;
	private final XComDateParser dateParser;
	private final XAuthenticationVerifier authenticationVerifier;

	@Override
	public String sourceCode() {
		return XComScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return XComScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		if (!properties.isEnabled()) {
			return ScraperExecutionResult.failed("X_COM scraper is disabled");
		}
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("X_COM search keyword must not be blank");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());
		String searchUrl = queryBuilder.buildSearchUrl(
				properties.getBaseUrl(),
				keyword.trim(),
				context.dateFrom(),
				context.dateTo(),
				properties.getSearchMode()
		);
		XScrapeDiagnostics diagnostics = new XScrapeDiagnostics();
		BrowserSessionOptions sessionOptions;
		try {
			sessionOptions = sessionOptions(diagnostics);
		} catch (BrowserEngineException exception) {
			log.warn("X auth state is not usable: {}", exception.getMessage());
			diagnostics.loginWallDetected++;
			return failed("AUTH_STATE_EXPIRED: " + exception.getMessage(), diagnostics);
		}
		log.info(
				"Starting X scraping: keyword={}, dateFrom={}, dateTo={}, mode={}, authStateUsed={}, maxScrollAttempts={}, maxPosts={}",
				keyword,
				context.dateFrom(),
				context.dateTo(),
				properties.getSearchMode(),
				diagnostics.authStateUsed,
				properties.getMaxScrollAttempts(),
				context.maxPosts()
		);

		try (BrowserSession session = browserSessionFactory.createSession(sessionOptions)) {
			BrowserPage page = session.newPage();
			if (properties.isAuthenticationRequired()) {
				XAuthenticationStatus authenticationStatus = verifyAuthenticatedSession(page);
				if (authenticationStatus != XAuthenticationStatus.AUTHENTICATED) {
					incrementAuthenticationDiagnostic(authenticationStatus, diagnostics);
					return failed(authenticationFailureMessage(authenticationStatus), diagnostics);
				}
			}
			page.navigate(searchUrl);
			page.waitForSelector("body", 15_000);
			page.waitForTimeout(1_500);
			FailureState initialFailure = detectFailureState(page.content(), page.url());
			if (initialFailure != FailureState.NONE) {
				incrementFailureDiagnostic(initialFailure, diagnostics);
				return failed(initialFailure, diagnostics);
			}
			if (properties.isPreferLatestTab()) {
				diagnostics.latestModeConfirmed = confirmLatestMode(page);
			}
			TimelineCollectionResult result = collectTimeline(page, context, keyword.trim(), searchUrl, diagnostics);
			log.info(
					"Finished X scraping: keyword={}, postsCollected={}, seen={}, uniqueStatusIds={}, promotedSkipped={}, "
							+ "duplicates={}, tooNew={}, tooOld={}, empty={}, dateFailures={}, scrollAttempts={}, noNewIterations={}, "
							+ "loginWall={}, rateLimited={}",
					keyword,
					result.posts().size(),
					diagnostics.timelinePostsSeen,
					diagnostics.uniqueStatusIds,
					diagnostics.promotedPostsSkipped,
					diagnostics.duplicatesSkipped,
					diagnostics.tooNewSkipped,
					diagnostics.tooOldSkipped,
					diagnostics.emptyPostsSkipped,
					diagnostics.dateParseFailures,
					diagnostics.scrollAttempts,
					diagnostics.noNewPostIterations,
					diagnostics.loginWallDetected,
					diagnostics.rateLimitDetected
			);
			if (!result.posts().isEmpty()) {
				return ScraperExecutionResult.success(result.posts());
			}
			if (result.visibleTweetsFound() && result.extractionFailures()) {
				return failed("TIMELINE_EXTRACTION_FAILED: visible X timeline posts were found but no valid posts were extracted", diagnostics);
			}
			return ScraperExecutionResult.empty();
		} catch (BrowserEngineException exception) {
			log.warn("X scraping failed: {}", exception.getMessage());
			return failed("TIMELINE_LOAD_FAILED: " + exception.getMessage(), diagnostics);
		} catch (RuntimeException exception) {
			log.error("Unexpected X scraping failure", exception);
			return failed("TIMELINE_LOAD_FAILED: " + exception.getMessage(), diagnostics);
		}
	}

	private BrowserSessionOptions sessionOptions(XScrapeDiagnostics diagnostics) {
		String authStatePath = properties.getAuthStatePath();
		if (authStatePath == null || authStatePath.isBlank()) {
			if (properties.isAuthenticationRequired()) {
				throw new BrowserEngineException("X authentication state is missing or expired. Regenerate it with the xAuthState Gradle task.");
			}
			return BrowserSessionOptions.defaults();
		}
		Path path = Path.of(authStatePath.trim());
		diagnostics.authStateUsed = true;
		return BrowserSessionOptions.withStorageState(path);
	}

	private XAuthenticationStatus verifyAuthenticatedSession(BrowserPage page) {
		page.navigate(homeUrl());
		page.waitForSelector("body", properties.getLoginTimeoutMs());
		page.waitForTimeout(1_000);
		return authenticationVerifier.verify(page);
	}

	private String homeUrl() {
		String baseUrl = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
				? XComScraperSupport.BASE_URL
				: properties.getBaseUrl().trim();
		String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		return normalized + "/home";
	}

	private String authenticationFailureMessage(XAuthenticationStatus status) {
		return switch (status) {
			case AUTH_STATE_EXPIRED, AUTH_REQUIRED ->
					"AUTH_STATE_EXPIRED: X authentication state is missing or expired. Regenerate it with the xAuthState Gradle task.";
			case CHALLENGE_REQUIRED ->
					"CHALLENGE_REQUIRED: Complete the X verification step in the opened browser and regenerate authentication state.";
			case RATE_LIMITED ->
					"RATE_LIMITED: X temporarily limited authenticated access. Retry later with the existing authentication state.";
			case UNKNOWN ->
					"AUTH_STATE_UNKNOWN: X authentication state could not be verified. Regenerate it with the xAuthState Gradle task.";
			case AUTHENTICATED -> "AUTHENTICATED";
		};
	}

	private boolean confirmLatestMode(BrowserPage page) {
		if (page.url() != null && page.url().contains("f=live")) {
			return true;
		}
		try {
			page.click(XComSelectors.LATEST_TAB);
			page.waitForTimeout(1_000);
			return page.url() != null && page.url().contains("f=live");
		} catch (BrowserEngineException exception) {
			log.warn("X Latest tab was not confirmed: {}", exception.getMessage());
			return false;
		}
	}

	private TimelineCollectionResult collectTimeline(
			BrowserPage page,
			ScraperExecutionContext context,
			String keyword,
			String searchUrl,
			XScrapeDiagnostics diagnostics
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenStatusIds = new LinkedHashSet<>();
		boolean visibleTweetsFound = false;
		boolean extractionFailures = false;

		for (int attempt = 0; attempt < Math.min(properties.getMaxScrollAttempts(), context.maxPages() * properties.getMaxScrollAttempts())
				&& posts.size() < context.maxPosts()
				&& diagnostics.noNewPostIterations < properties.getNoNewPostLimit(); attempt++) {
			Document document = Jsoup.parse(page.content(), page.url() == null ? XComScraperSupport.BASE_URL : page.url());
			FailureState failureState = detectFailureState(document.html(), page.url());
			if (failureState != FailureState.NONE) {
				incrementFailureDiagnostic(failureState, diagnostics);
				extractionFailures = true;
				break;
			}
			List<Element> articles = document.select(XComSelectors.TWEET_ARTICLE);
			diagnostics.timelinePostsSeen += articles.size();
			visibleTweetsFound = visibleTweetsFound || !articles.isEmpty();
			int acceptedThisRound = 0;
			for (Element article : articles) {
				if (posts.size() >= context.maxPosts()) {
					break;
				}
				ParseAttempt parsed = parseArticle(article, context, keyword, searchUrl, diagnostics);
				if (parsed.skipReason() == SkipReason.PROMOTED) {
					diagnostics.promotedPostsSkipped++;
					continue;
				}
				if (parsed.candidate() == null) {
					extractionFailures = extractionFailures || parsed.skipReason().isExtractionFailure();
					incrementSkip(parsed.skipReason(), diagnostics);
					continue;
				}
				XPostCandidate candidate = parsed.candidate();
				if (!seenStatusIds.add(candidate.externalPostId())) {
					diagnostics.duplicatesSkipped++;
					continue;
				}
				diagnostics.uniqueStatusIds = seenStatusIds.size();
				if (DateRangeValidator.isAfterRange(candidate.postDate(), context.dateTo())) {
					diagnostics.tooNewSkipped++;
					continue;
				}
				if (DateRangeValidator.isBeforeRange(candidate.postDate(), context.dateFrom())) {
					diagnostics.tooOldSkipped++;
					if (diagnostics.latestModeConfirmed) {
						diagnostics.noNewPostIterations = properties.getNoNewPostLimit();
						break;
					}
					continue;
				}
				posts.add(toScrapedPost(candidate, diagnostics));
				acceptedThisRound++;
				diagnostics.postsCollected = posts.size();
			}
			diagnostics.noNewPostIterations = acceptedThisRound == 0 ? diagnostics.noNewPostIterations + 1 : 0;
			if (posts.size() >= context.maxPosts() || diagnostics.noNewPostIterations >= properties.getNoNewPostLimit()) {
				break;
			}
			diagnostics.scrollAttempts++;
			page.scrollBy(SCROLL_PIXELS, properties.getScrollDelayMs());
		}
		return new TimelineCollectionResult(posts, visibleTweetsFound, extractionFailures);
	}

	ParseAttempt parseArticle(
			Element article,
			ScraperExecutionContext context,
			String keyword,
			String searchUrl,
			XScrapeDiagnostics diagnostics
	) {
		if (!properties.isIncludePromoted() && isPromoted(article)) {
			return ParseAttempt.skipped(SkipReason.PROMOTED);
		}
		Optional<XStatusUrl> statusUrl = statusUrl(article);
		if (statusUrl.isEmpty()) {
			return ParseAttempt.skipped(SkipReason.MISSING_STATUS_URL);
		}
		Optional<OffsetDateTime> postDate = dateParser.parseDatetime(timeElement(article).map(time -> time.attr("datetime")).orElse(null));
		if (postDate.isEmpty()) {
			return ParseAttempt.skipped(SkipReason.INVALID_DATE);
		}
		List<ScrapedMediaDTO> media = extractMedia(article, statusUrl.get().canonicalUrl(), diagnostics);
		String text = extractText(article);
		if ((text == null || text.isBlank()) && media.isEmpty()) {
			return ParseAttempt.skipped(SkipReason.EMPTY_POST);
		}
		if (text == null || text.isBlank()) {
			diagnostics.mediaOnlyPostsCollected++;
			text = "";
		}
		ScrapedAuthorDTO author = extractAuthor(article, statusUrl.get());
		String language = XComScraperSupport.normalizeLanguage(extractLanguage(article), context.keyword().getLanguage().name());
		Map<String, Object> metadata = metadata(keyword, searchUrl, article, diagnostics);
		return ParseAttempt.candidate(new XPostCandidate(
				statusUrl.get().externalPostId(),
				statusUrl.get().canonicalUrl(),
				postDate.get(),
				author,
				text,
				language,
				media,
				metadata
		));
	}

	private Optional<XStatusUrl> statusUrl(Element article) {
		return article.select(XComSelectors.STATUS_LINK)
				.stream()
				.map(link -> firstNonBlank(link.attr("href"), link.attr("abs:href")))
				.map(XComScraperSupport::parseStatusUrl)
				.flatMap(Optional::stream)
				.findFirst();
	}

	private Optional<Element> timeElement(Element article) {
		return Optional.ofNullable(article.selectFirst(XComSelectors.TWEET_TIME));
	}

	private boolean isPromoted(Element article) {
		String text = article.text();
		String labels = article.select("[aria-label], [data-testid]")
				.stream()
				.limit(100)
				.map(element -> element.attr("aria-label") + " " + element.attr("data-testid"))
				.reduce("", (left, right) -> left + " " + right);
		return XComScraperSupport.isPromotedText(text + " " + labels);
	}

	private String extractText(Element article) {
		Element textElement = article.selectFirst(XComSelectors.TWEET_TEXT);
		if (textElement == null) {
			return null;
		}
		String text = normalizeText(textElement.text());
		return text != null && text.length() >= MIN_TEXT_LENGTH ? text : null;
	}

	private String extractLanguage(Element article) {
		Element textElement = article.selectFirst(XComSelectors.TWEET_TEXT);
		if (textElement == null) {
			return null;
		}
		return firstNonBlank(textElement.attr("lang"), textElement.parents().stream()
				.map(parent -> parent.attr("lang"))
				.filter(value -> value != null && !value.isBlank())
				.findFirst()
				.orElse(null));
	}

	private ScrapedAuthorDTO extractAuthor(Element article, XStatusUrl statusUrl) {
		String username = statusUrl.username();
		Element userElement = article.selectFirst(XComSelectors.USER_NAME);
		String displayName = displayName(userElement, username);
		String avatar = Optional.ofNullable(article.selectFirst(XComSelectors.AVATAR))
				.map(image -> firstNonBlank(image.attr("src"), image.attr("data-src")))
				.filter(XComScraperSupport::isAllowedAvatarUrl)
				.orElse(null);
		return new ScrapedAuthorDTO(
				username,
				username,
				displayName,
				"https://x.com/" + username,
				avatar
		);
	}

	private String displayName(Element userElement, String username) {
		if (userElement == null) {
			return username;
		}
		String text = normalizeText(userElement.text());
		if (text == null || text.isBlank()) {
			return username;
		}
		String marker = "@" + username;
		int markerIndex = text.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
		if (markerIndex > 0) {
			return text.substring(0, markerIndex).trim();
		}
		return text.lines().findFirst().orElse(username).trim();
	}

	private List<ScrapedMediaDTO> extractMedia(Element article, String baseUrl, XScrapeDiagnostics diagnostics) {
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		for (Element image : article.select(XComSelectors.TWEET_PHOTO)) {
			String rawUrl = firstNonBlank(image.attr("src"), image.attr("data-src"), firstSrcsetUrl(image.attr("srcset")));
			addMedia(mediaByUrl, baseUrl, rawUrl, MediaType.IMAGE);
		}
		for (Element video : article.select(XComSelectors.VIDEO)) {
			diagnostics.videosDetected++;
			String rawUrl = firstNonBlank(video.attr("poster"), video.attr("src"));
			addMedia(mediaByUrl, baseUrl, rawUrl, MediaType.VIDEO);
		}
		List<ScrapedMediaDTO> media = new ArrayList<>();
		int position = 0;
		for (Map.Entry<String, MediaType> entry : mediaByUrl.entrySet()) {
			media.add(new ScrapedMediaDTO(entry.getKey(), entry.getValue(), position++));
			if (entry.getValue() == MediaType.IMAGE) {
				diagnostics.imagesCollected++;
			}
		}
		return media;
	}

	private void addMedia(Map<String, MediaType> mediaByUrl, String baseUrl, String rawUrl, MediaType mediaType) {
		if (rawUrl == null || !XComScraperSupport.isAllowedMediaUrl(rawUrl)) {
			return;
		}
		String normalizedUrl;
		try {
			normalizedUrl = UrlNormalizer.resolve(baseUrl, rawUrl);
		} catch (IllegalArgumentException exception) {
			return;
		}
		if (XComScraperSupport.isAllowedMediaUrl(normalizedUrl)) {
			mediaByUrl.putIfAbsent(normalizedUrl, mediaType);
		}
	}

	private String firstSrcsetUrl(String srcset) {
		if (srcset == null || srcset.isBlank()) {
			return null;
		}
		String firstCandidate = srcset.split(",")[0].trim();
		return firstCandidate.isBlank() ? null : firstCandidate.split("\\s+")[0].trim();
	}

	private Map<String, Object> metadata(String keyword, String searchUrl, Element article, XScrapeDiagnostics diagnostics) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", XComScraperSupport.SOURCE_CODE);
		metadata.put("keyword", keyword);
		metadata.put("searchQuery", searchUrl);
		metadata.put("searchMode", properties.getSearchMode().name());
		boolean isReply = normalizedArticleText(article).contains("replying to");
		boolean isRepost = normalizedArticleText(article).contains("reposted") || normalizedArticleText(article).contains("retweeted");
		metadata.put("isReply", isReply);
		metadata.put("isRepost", isRepost);
		metadata.put("hasVideo", article.selectFirst(XComSelectors.VIDEO) != null);
		if (isReply) {
			diagnostics.replyPostsCollected++;
		}
		if (isRepost) {
			diagnostics.repostsCollected++;
		}
		return metadata;
	}

	private ScrapedPostDTO toScrapedPost(XPostCandidate candidate, XScrapeDiagnostics diagnostics) {
		Map<String, Object> metadata = new LinkedHashMap<>(candidate.metadata());
		metadata.put("diagnostics", diagnostics.toMetadata());
		return new ScrapedPostDTO(
				candidate.externalPostId(),
				candidate.postUrl(),
				candidate.postDate(),
				candidate.author(),
				candidate.text(),
				candidate.language(),
				candidate.media(),
				metadata
		);
	}

	private FailureState detectFailureState(String html, String url) {
		Document document = Jsoup.parse(html == null ? "" : html);
		boolean hasTweetArticles = !document.select(XComSelectors.TWEET_ARTICLE).isEmpty();
		boolean hasLoginControl = document.selectFirst(XComSelectors.LOGIN_INPUT + ", " + XComSelectors.LOGIN_BUTTON) != null;
		String normalizedUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
		String normalizedText = normalizeText(document.text()).toLowerCase(Locale.ROOT);
		if (normalizedUrl.contains("/login") || normalizedUrl.contains("/i/flow/login") || hasLoginControl
				|| (!hasTweetArticles && (normalizedText.contains("sign in")
				|| normalizedText.contains("log in") || normalizedText.contains("create account")))) {
			return FailureState.AUTH_REQUIRED;
		}
		if (normalizedText.contains("rate limit") || normalizedText.contains("too many requests")) {
			return FailureState.RATE_LIMITED;
		}
		if (normalizedText.contains("something went wrong") || normalizedText.contains("try again")) {
			return FailureState.SEARCH_UNAVAILABLE;
		}
		return FailureState.NONE;
	}

	private void incrementFailureDiagnostic(FailureState failureState, XScrapeDiagnostics diagnostics) {
		switch (failureState) {
			case AUTH_REQUIRED, AUTH_STATE_EXPIRED -> diagnostics.loginWallDetected++;
			case RATE_LIMITED -> diagnostics.rateLimitDetected++;
			default -> {
			}
		}
	}

	private void incrementAuthenticationDiagnostic(XAuthenticationStatus status, XScrapeDiagnostics diagnostics) {
		switch (status) {
			case AUTH_REQUIRED, AUTH_STATE_EXPIRED, CHALLENGE_REQUIRED, UNKNOWN -> diagnostics.loginWallDetected++;
			case RATE_LIMITED -> diagnostics.rateLimitDetected++;
			default -> {
			}
		}
	}

	private void incrementSkip(SkipReason reason, XScrapeDiagnostics diagnostics) {
		switch (reason) {
			case MISSING_STATUS_URL, EMPTY_POST -> diagnostics.emptyPostsSkipped++;
			case INVALID_DATE -> diagnostics.dateParseFailures++;
			default -> {
			}
		}
	}

	private ScraperExecutionResult failed(FailureState state, XScrapeDiagnostics diagnostics) {
		return failed(state.name() + ": X scraping cannot continue", diagnostics);
	}

	private ScraperExecutionResult failed(String message, XScrapeDiagnostics diagnostics) {
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

	private String normalizedArticleText(Element article) {
		return normalizeText(article == null ? null : article.text()).toLowerCase(Locale.ROOT);
	}

	private static String normalizeText(String text) {
		if (text == null) {
			return null;
		}
		String normalized = text
				.replace('\u00A0', ' ')
				.replaceAll("[\\t\\x0B\\f\\r ]+", " ")
				.replaceAll(" *\\n+ *", "\n")
				.trim();
		List<String> lines = normalized.lines()
				.map(String::trim)
				.filter(line -> !line.isBlank())
				.toList();
		return String.join("\n", lines);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	enum SkipReason {
		PROMOTED(false),
		MISSING_STATUS_URL(true),
		INVALID_DATE(true),
		EMPTY_POST(true);

		private final boolean extractionFailure;

		SkipReason(boolean extractionFailure) {
			this.extractionFailure = extractionFailure;
		}

		boolean isExtractionFailure() {
			return extractionFailure;
		}
	}

	enum FailureState {
		NONE,
		AUTH_REQUIRED,
		AUTH_STATE_EXPIRED,
		RATE_LIMITED,
		SEARCH_UNAVAILABLE
	}

	record ParseAttempt(XPostCandidate candidate, SkipReason skipReason) {

		static ParseAttempt candidate(XPostCandidate candidate) {
			return new ParseAttempt(candidate, null);
		}

		static ParseAttempt skipped(SkipReason reason) {
			return new ParseAttempt(null, reason);
		}
	}

	private record TimelineCollectionResult(
			List<ScrapedPostDTO> posts,
			boolean visibleTweetsFound,
			boolean extractionFailures
	) {
	}
}
