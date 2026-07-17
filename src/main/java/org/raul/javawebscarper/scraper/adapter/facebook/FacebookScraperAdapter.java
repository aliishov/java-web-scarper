package org.raul.javawebscarper.scraper.adapter.facebook;

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
import java.util.Comparator;
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
public class FacebookScraperAdapter implements ScraperAdapter {

	private static final int MIN_KEYWORD_LENGTH = 1;
	private static final int SCROLL_PIXELS = 1_100;
	private static final int MAX_AUTHOR_LENGTH = 120;
	private static final int MIN_TEXT_LENGTH = 2;

	private final BrowserSessionFactory browserSessionFactory;
	private final FacebookProperties properties;
	private final FacebookSearchQueryBuilder searchQueryBuilder;
	private final FacebookDateParser dateParser;
	private final FacebookAuthenticationVerifier authenticationVerifier;

	@Override
	public String sourceCode() {
		return FacebookScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return FacebookScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		if (!properties.isEnabled()) {
			return ScraperExecutionResult.failed("FACEBOOK_SCRAPER_DISABLED: Facebook scraper is disabled");
		}
		String keyword = context.keyword().getWord();
		if (keyword == null || keyword.trim().length() < MIN_KEYWORD_LENGTH) {
			return ScraperExecutionResult.failed("FACEBOOK_BAD_REQUEST: search keyword must not be blank");
		}
		DateRangeValidator.validate(context.dateFrom(), context.dateTo());

		FacebookScrapeDiagnostics diagnostics = new FacebookScrapeDiagnostics();
		String searchUrl = searchQueryBuilder.buildSearchUrl(properties.getBaseUrl(), keyword.trim(), properties.getSearchMode());
		diagnostics.searchQuery = searchUrl;
		diagnostics.postsFilterConfirmed = searchUrl.contains("/search/posts/");
		diagnostics.recentFilterConfirmed = properties.getSearchMode() == FacebookSearchMode.RECENT && searchUrl.contains("filters=");

		BrowserSessionOptions sessionOptions;
		try {
			sessionOptions = sessionOptions(diagnostics);
		} catch (BrowserEngineException exception) {
			log.warn("Facebook auth state is not usable: {}", exception.getMessage());
			return failed("FACEBOOK_AUTH_STATE_MISSING: " + exception.getMessage(), diagnostics);
		}

		log.info(
				"Starting Facebook scraping: keyword={}, dateFrom={}, dateTo={}, mode={}, authStateUsed={}, maxScrollAttempts={}, maxPosts={}",
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
				FacebookAuthenticationStatus authenticationStatus = verifyAuthenticatedSession(page);
				diagnostics.authenticationStatus = authenticationStatus;
				if (authenticationStatus != FacebookAuthenticationStatus.AUTHENTICATED) {
					incrementAuthenticationDiagnostic(authenticationStatus, diagnostics);
					return failed(authenticationFailureMessage(authenticationStatus), diagnostics);
				}
			}
			openSearchPage(page, keyword.trim(), searchUrl);

			TimelineCollectionResult result = collectTimeline(page, context, keyword.trim(), diagnostics);
			log.info(
					"Finished Facebook scraping: keyword={}, postsCollected={}, containersSeen={}, validCandidates={}, duplicates={}, "
							+ "sponsoredSkipped={}, reelsSkipped={}, tooNew={}, tooOld={}, empty={}, dateFailures={}, "
							+ "scrollAttempts={}, noNewIterations={}, checkpoint={}, rateLimit={}",
					keyword,
					result.posts().size(),
					diagnostics.containersSeen,
					diagnostics.validPostCandidates,
					diagnostics.duplicatePostsSkipped,
					diagnostics.sponsoredPostsSkipped,
					diagnostics.reelsSkipped,
					diagnostics.tooNewSkipped,
					diagnostics.tooOldSkipped,
					diagnostics.emptyPostsSkipped,
					diagnostics.dateParseFailures,
					diagnostics.scrollAttempts,
					diagnostics.noNewPostIterations,
					diagnostics.checkpointDetected,
					diagnostics.rateLimitDetected
			);
			if (!result.posts().isEmpty()) {
				return ScraperExecutionResult.success(result.posts());
			}
			if (result.visibleContainersFound() && result.extractionFailures()) {
				return failed("FACEBOOK_EXTRACTION_FAILED: visible Facebook containers were found but no valid posts were extracted", diagnostics);
			}
			return ScraperExecutionResult.empty();
		} catch (BrowserEngineException exception) {
			log.warn("Facebook scraping failed: {}", exception.getMessage());
			return failed("FACEBOOK_TIMELINE_LOAD_FAILED: " + exception.getMessage(), diagnostics);
		} catch (RuntimeException exception) {
			log.error("Unexpected Facebook scraping failure", exception);
			return failed("FACEBOOK_TIMELINE_LOAD_FAILED: " + exception.getMessage(), diagnostics);
		}
	}

	private BrowserSessionOptions sessionOptions(FacebookScrapeDiagnostics diagnostics) {
		String authStatePath = properties.getAuthStatePath();
		if (authStatePath == null || authStatePath.isBlank()) {
			if (properties.isAuthenticationRequired()) {
				throw new BrowserEngineException("Facebook authentication state file is not configured. Set FACEBOOK_AUTH_STATE_PATH and create it with facebookAuthStateInteractive.");
			}
			return BrowserSessionOptions.defaults();
		}
		diagnostics.authStateUsed = true;
		return new BrowserSessionOptions(Path.of(authStatePath.trim()), "en-US", "Asia/Baku", null, Map.of());
	}

	private FacebookAuthenticationStatus verifyAuthenticatedSession(BrowserPage page) {
		page.navigate(homeUrl());
		page.waitForSelector("body", properties.getAuthenticationTimeoutMs());
		page.waitForTimeout(properties.getActionDelayMs());
		return authenticationVerifier.verify(page);
	}

	private void openSearchPage(BrowserPage page, String keyword, String searchUrl) {
		try {
			page.navigate(homeUrl());
			page.waitForSelector("body", properties.getTimelineLoadTimeoutMs());
			page.waitForTimeout(properties.getActionDelayMs());
			page.fill(FacebookSelectors.SEARCH_INPUT, keyword);
			page.press(FacebookSelectors.SEARCH_INPUT, "Enter");
			page.waitForTimeout(properties.getScrollDelayMs());
			if (page.url() != null && page.url().contains("/search/")) {
				if (!page.url().contains("/search/posts/")) {
					page.navigate(searchUrl);
				}
				page.waitForSelector("body", properties.getTimelineLoadTimeoutMs());
				return;
			}
			log.warn("Facebook UI search did not navigate to search results; using direct search URL fallback");
		} catch (BrowserEngineException exception) {
			log.warn("Facebook UI search failed; using direct search URL fallback: {}", exception.getMessage());
		}
		page.navigate(searchUrl);
		page.waitForSelector("body", properties.getTimelineLoadTimeoutMs());
		page.waitForTimeout(properties.getActionDelayMs());
	}

	private String homeUrl() {
		String baseUrl = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
				? FacebookScraperSupport.BASE_URL
				: properties.getBaseUrl().trim();
		return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
	}

	private TimelineCollectionResult collectTimeline(
			BrowserPage page,
			ScraperExecutionContext context,
			String keyword,
			FacebookScrapeDiagnostics diagnostics
	) {
		List<ScrapedPostDTO> posts = new ArrayList<>();
		Set<String> seenPostIds = new LinkedHashSet<>();
		boolean visibleContainersFound = false;
		boolean extractionFailures = false;

		for (int attempt = 0;
				attempt < Math.min(properties.getMaxScrollAttempts(), context.maxPages() * properties.getMaxScrollAttempts())
						&& posts.size() < context.maxPosts()
						&& diagnostics.noNewPostIterations < properties.getNoNewPostLimit();
				attempt++) {
			Document document = Jsoup.parse(page.content(), page.url() == null ? FacebookScraperSupport.BASE_URL : page.url());
			FacebookAuthenticationStatus pageState = FacebookAuthenticationPageInspector.inspect(page.url(), document.html());
			if (isHardFailure(pageState)) {
				diagnostics.authenticationStatus = pageState;
				incrementAuthenticationDiagnostic(pageState, diagnostics);
				extractionFailures = true;
				break;
			}
			List<Element> containers = discoverContainers(document);
			diagnostics.containersSeen += containers.size();
			visibleContainersFound = visibleContainersFound || !containers.isEmpty();
			int acceptedThisRound = 0;
			for (Element container : containers) {
				if (posts.size() >= context.maxPosts()) {
					break;
				}
				ParseAttempt parsed = parseContainer(container, context, keyword, diagnostics);
				if (parsed.skipReason() == SkipReason.SPONSORED) {
					diagnostics.sponsoredPostsSkipped++;
					continue;
				}
				if (parsed.skipReason() == SkipReason.REEL_DISABLED) {
					diagnostics.reelsSkipped++;
					continue;
				}
				if (parsed.candidate() == null) {
					extractionFailures = extractionFailures || parsed.skipReason().isExtractionFailure();
					incrementSkip(parsed.skipReason(), diagnostics);
					continue;
				}
				FacebookPostCandidate candidate = parsed.candidate();
				if (!seenPostIds.add(candidate.externalPostId())) {
					diagnostics.duplicatePostsSkipped++;
					continue;
				}
				if (DateRangeValidator.isAfterRange(candidate.postDate(), context.dateTo())) {
					diagnostics.tooNewSkipped++;
					continue;
				}
				if (DateRangeValidator.isBeforeRange(candidate.postDate(), context.dateFrom())) {
					diagnostics.tooOldSkipped++;
					if (diagnostics.recentFilterConfirmed) {
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
		return new TimelineCollectionResult(posts, visibleContainersFound, extractionFailures);
	}

	ParseAttempt parseContainer(
			Element container,
			ScraperExecutionContext context,
			String keyword,
			FacebookScrapeDiagnostics diagnostics
	) {
		if (!properties.isIncludeSponsored() && isSponsored(container)) {
			return ParseAttempt.skipped(SkipReason.SPONSORED);
		}
		Optional<FacebookPostUrl> postUrl = postUrl(container);
		if (postUrl.isEmpty()) {
			return ParseAttempt.skipped(SkipReason.MISSING_POST_URL);
		}
		if (postUrl.get().isReel() && !properties.isIncludeReels()) {
			return ParseAttempt.skipped(SkipReason.REEL_DISABLED);
		}
		Optional<OffsetDateTime> postDate = extractDate(container);
		if (postDate.isEmpty()) {
			return ParseAttempt.skipped(SkipReason.INVALID_DATE);
		}
		List<ScrapedMediaDTO> media = extractMedia(container, postUrl.get().canonicalUrl(), diagnostics);
		String text = extractText(container);
		if ((text == null || text.isBlank()) && media.isEmpty()) {
			return ParseAttempt.skipped(SkipReason.EMPTY_POST);
		}
		ScrapedAuthorDTO author = extractAuthor(container, postUrl.get());
		String language = FacebookScraperSupport.normalizeLanguage(extractLanguage(container), context.keyword().getLanguage().name());
		Map<String, Object> metadata = metadata(keyword, postUrl.get(), container);
		diagnostics.validPostCandidates++;
		if (postUrl.get().type() == FacebookPostType.GROUP_POST) {
			diagnostics.groupPostsCollected++;
		}
		if (Boolean.TRUE.equals(metadata.get("isSharedPost"))) {
			diagnostics.sharedPostsCollected++;
		}
		return ParseAttempt.candidate(new FacebookPostCandidate(
				postUrl.get().externalPostId(),
				postUrl.get().canonicalUrl(),
				postUrl.get().type(),
				postDate.get(),
				author,
				text == null ? "" : text,
				language,
				media,
				metadata
		));
	}

	private List<Element> discoverContainers(Document document) {
		LinkedHashSet<Element> containers = new LinkedHashSet<>(document.select(FacebookSelectors.POST_CONTAINER));
		for (Element link : document.select(FacebookSelectors.POST_LINK)) {
			FacebookPostUrlParser.parse(link.attr("href")).ifPresent(ignored -> {
				Element current = link;
				for (int depth = 0; current != null && depth < 8; depth++, current = current.parent()) {
					if (looksLikePostContainer(current)) {
						containers.add(current);
						break;
					}
				}
			});
		}
		return containers.stream()
				.filter(this::looksLikePostContainer)
				.filter(candidate -> containers.stream().noneMatch(other -> other != candidate
						&& candidate.parents().contains(other)
						&& looksLikePostContainer(other)))
				.toList();
	}

	private boolean looksLikePostContainer(Element element) {
		if (element == null) {
			return false;
		}
		boolean hasPostUrl = postUrl(element).isPresent();
		boolean hasAuthor = !authorCandidates(element).isEmpty();
		boolean hasContent = element.selectFirst(FacebookSelectors.MESSAGE_NODE + ", img[src], video") != null;
		boolean hasDate = extractDate(element).isPresent();
		boolean tooBroad = element.parents().isEmpty() || "body".equalsIgnoreCase(element.tagName()) || element.select(FacebookSelectors.POST_LINK).size() > 8;
		return hasPostUrl && (hasContent || hasAuthor) && (hasDate || hasAuthor) && !tooBroad;
	}

	private Optional<FacebookPostUrl> postUrl(Element container) {
		return container.select(FacebookSelectors.POST_LINK)
				.stream()
				.map(link -> FacebookScraperSupport.firstNonBlank(link.attr("href"), link.attr("abs:href")))
				.map(FacebookPostUrlParser::parse)
				.flatMap(Optional::stream)
				.findFirst()
				.or(() -> container.select("a[href]").stream()
						.map(link -> FacebookScraperSupport.firstNonBlank(link.attr("href"), link.attr("abs:href")))
						.map(FacebookPostUrlParser::parse)
						.flatMap(Optional::stream)
						.findFirst());
	}

	private Optional<OffsetDateTime> extractDate(Element container) {
		return container.select(FacebookSelectors.DATE_CANDIDATE)
				.stream()
				.flatMap(element -> List.of(
								element.attr("datetime"),
								element.attr("data-utime"),
								element.attr("title"),
								element.attr("aria-label"),
								element.text())
						.stream())
				.map(dateParser::parse)
				.flatMap(Optional::stream)
				.findFirst();
	}

	private ScrapedAuthorDTO extractAuthor(Element container, FacebookPostUrl postUrl) {
		return authorCandidates(container).stream()
				.findFirst()
				.map(candidate -> new ScrapedAuthorDTO(
						authorExternalId(candidate.profileUrl(), postUrl.authorExternalId()),
						authorUsername(candidate.profileUrl(), candidate.displayName()),
						candidate.displayName(),
						candidate.profileUrl(),
						candidate.avatarUrl()
				))
				.orElseGet(() -> new ScrapedAuthorDTO(
						postUrl.authorExternalId(),
						postUrl.authorExternalId(),
						postUrl.authorExternalId(),
						postUrl.authorExternalId() == null ? FacebookScraperSupport.BASE_URL : FacebookScraperSupport.BASE_URL + postUrl.authorExternalId(),
						null
				));
	}

	private List<AuthorCandidate> authorCandidates(Element container) {
		List<Element> scopes = new ArrayList<>(container.select(FacebookSelectors.PROFILE_NAME));
		if (scopes.isEmpty()) {
			scopes.add(container);
		}
		List<AuthorCandidate> candidates = new ArrayList<>();
		for (Element scope : scopes) {
			for (Element link : scope.select("a[role=link][href], a[href]")) {
				if (FacebookPostUrlParser.parse(link.attr("href")).isPresent()) {
					continue;
				}
				String displayName = FacebookScraperSupport.normalizeText(link.text());
				if (displayName.isBlank() || displayName.length() > MAX_AUTHOR_LENGTH || dateParser.looksLikeDate(displayName)
						|| FacebookScraperSupport.isUiText(displayName)) {
					continue;
				}
				String profileUrl = normalizeProfileUrl(link.attr("href"));
				if (profileUrl == null || isNonAuthorProfileUrl(profileUrl)) {
					continue;
				}
				String avatar = scope.select("img[src], img[data-src]").stream()
						.map(image -> FacebookScraperSupport.firstNonBlank(image.attr("src"), image.attr("data-src")))
						.filter(FacebookScraperSupport::isAllowedAvatarUrl)
						.findFirst()
						.orElse(null);
				candidates.add(new AuthorCandidate(displayName, profileUrl, avatar, scoreAuthor(link, displayName)));
			}
		}
		return candidates.stream()
				.sorted(Comparator.comparingInt(AuthorCandidate::score).reversed())
				.toList();
	}

	private int scoreAuthor(Element link, String displayName) {
		int score = 100 - displayName.length();
		if (link.parents().stream().anyMatch(parent -> "strong".equalsIgnoreCase(parent.tagName())
				|| parent.tagName().matches("h[1-6]"))) {
			score += 40;
		}
		return score;
	}

	private String extractText(Element container) {
		Element message = container.selectFirst(FacebookSelectors.MESSAGE_NODE);
		String messageText = cleanPostText(message == null ? null : message.text());
		if (!messageText.isBlank() && !FacebookScraperSupport.isUiText(messageText)) {
			return messageText;
		}
		return container.select("div[dir=auto], span[dir=auto], p")
				.stream()
				.filter(element -> element.closest(FacebookSelectors.PROFILE_NAME) == null)
				.filter(element -> element.closest("button, [role=button], [role=menu], [role=tooltip]") == null)
				.filter(element -> element.closest("figure, [data-visualcompletion=media-vc-image]") == null)
				.map(Element::text)
				.map(this::cleanPostText)
				.filter(text -> text.length() >= MIN_TEXT_LENGTH)
				.filter(text -> !FacebookScraperSupport.isUiText(text))
				.filter(text -> !dateParser.looksLikeDate(text))
				.max(Comparator.comparingInt(String::length))
				.orElse(null);
	}

	private List<ScrapedMediaDTO> extractMedia(Element container, String baseUrl, FacebookScrapeDiagnostics diagnostics) {
		Map<String, MediaType> mediaByUrl = new LinkedHashMap<>();
		for (Element image : container.select("img[src], img[data-src]")) {
			if (image.closest(FacebookSelectors.PROFILE_NAME) != null || isAvatarOrIcon(image)) {
				continue;
			}
			String rawUrl = FacebookScraperSupport.firstNonBlank(image.attr("src"), image.attr("data-src"), firstSrcsetUrl(image.attr("srcset")));
			addMedia(mediaByUrl, baseUrl, rawUrl, MediaType.IMAGE);
		}
		for (Element video : container.select("video")) {
			diagnostics.videosDetected++;
			String rawVideoUrl = FacebookScraperSupport.firstNonBlank(video.attr("src"), video.select("source[src]").stream()
					.map(source -> source.attr("src"))
					.filter(value -> value != null && !value.isBlank())
					.findFirst()
					.orElse(null));
			addMedia(mediaByUrl, baseUrl, rawVideoUrl, MediaType.VIDEO);
			String poster = FacebookScraperSupport.firstNonBlank(video.attr("poster"));
			addMedia(mediaByUrl, baseUrl, poster, MediaType.IMAGE);
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
		if (rawUrl == null || !FacebookScraperSupport.isAllowedMediaUrl(rawUrl)) {
			return;
		}
		try {
			String normalizedUrl = UrlNormalizer.resolve(baseUrl, rawUrl);
			if (FacebookScraperSupport.isAllowedMediaUrl(normalizedUrl)) {
				mediaByUrl.putIfAbsent(normalizedUrl, mediaType);
			}
		} catch (IllegalArgumentException ignored) {
			// Ignore malformed media URLs from volatile Facebook markup.
		}
	}

	private boolean isAvatarOrIcon(Element image) {
		String marker = (image.attr("alt") + " " + image.attr("aria-label") + " " + image.attr("class")).toLowerCase(Locale.ROOT);
		String src = FacebookScraperSupport.firstNonBlank(image.attr("src"), image.attr("data-src"));
		String normalizedSrc = src == null ? "" : src.toLowerCase(Locale.ROOT);
		return marker.contains("avatar")
				|| marker.contains("profile picture")
				|| marker.contains("emoji")
				|| marker.contains("icon")
				|| normalizedSrc.contains("emoji")
				|| normalizedSrc.contains("rsrc.php");
	}

	private String extractLanguage(Element container) {
		return FacebookScraperSupport.firstNonBlank(
				container.attr("lang"),
				container.parents().stream()
						.map(parent -> parent.attr("lang"))
						.filter(value -> value != null && !value.isBlank())
						.findFirst()
						.orElse(null)
		);
	}

	private Map<String, Object> metadata(String keyword, FacebookPostUrl postUrl, Element container) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", FacebookScraperSupport.SOURCE_CODE);
		metadata.put("keyword", keyword);
		metadata.put("searchMode", properties.getSearchMode().name());
		metadata.put("postType", postUrl.type().name());
		metadata.put("isGroupPost", postUrl.type() == FacebookPostType.GROUP_POST);
		metadata.put("isSharedPost", isSharedPost(container));
		metadata.put("hasVideo", container.selectFirst("video") != null);
		return metadata;
	}

	private ScrapedPostDTO toScrapedPost(FacebookPostCandidate candidate, FacebookScrapeDiagnostics diagnostics) {
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

	private boolean isSponsored(Element container) {
		return FacebookScraperSupport.isSponsoredText(container.text() + " "
				+ container.select("[aria-label], [title]").stream()
				.limit(100)
				.map(element -> element.attr("aria-label") + " " + element.attr("title"))
				.reduce("", (left, right) -> left + " " + right));
	}

	private boolean isSharedPost(Element container) {
		String text = FacebookScraperSupport.normalizeText(container.text()).toLowerCase(Locale.ROOT);
		return text.contains("shared")
				|| text.contains("поделился")
				|| text.contains("paylaşdı")
				|| text.contains("paylasdi");
	}

	private boolean isHardFailure(FacebookAuthenticationStatus status) {
		return status == FacebookAuthenticationStatus.AUTH_REQUIRED
				|| status == FacebookAuthenticationStatus.AUTH_STATE_EXPIRED
				|| status == FacebookAuthenticationStatus.CHECKPOINT_REQUIRED
				|| status == FacebookAuthenticationStatus.CHALLENGE_REQUIRED
				|| status == FacebookAuthenticationStatus.RATE_LIMITED
				|| status == FacebookAuthenticationStatus.ACCOUNT_RESTRICTED;
	}

	private void incrementAuthenticationDiagnostic(FacebookAuthenticationStatus status, FacebookScrapeDiagnostics diagnostics) {
		switch (status) {
			case CHECKPOINT_REQUIRED -> diagnostics.checkpointDetected = true;
			case RATE_LIMITED -> diagnostics.rateLimitDetected = true;
			default -> {
			}
		}
	}

	private void incrementSkip(SkipReason reason, FacebookScrapeDiagnostics diagnostics) {
		switch (reason) {
			case MISSING_POST_URL, EMPTY_POST -> diagnostics.emptyPostsSkipped++;
			case INVALID_DATE -> diagnostics.dateParseFailures++;
			default -> {
			}
		}
	}

	private String authenticationFailureMessage(FacebookAuthenticationStatus status) {
		return switch (status) {
			case AUTH_REQUIRED, AUTH_STATE_EXPIRED ->
					"FACEBOOK_AUTH_STATE_EXPIRED: Facebook authentication state is missing or expired. Regenerate it with facebookAuthStateInteractive.";
			case CHECKPOINT_REQUIRED ->
					"FACEBOOK_CHECKPOINT_REQUIRED: Complete the Facebook checkpoint manually and regenerate authentication state.";
			case CHALLENGE_REQUIRED ->
					"FACEBOOK_CHALLENGE_REQUIRED: Complete the Facebook verification step manually and regenerate authentication state.";
			case RATE_LIMITED ->
					"FACEBOOK_RATE_LIMITED: Facebook temporarily limited authenticated access. Retry later.";
			case ACCOUNT_RESTRICTED ->
					"FACEBOOK_ACCOUNT_RESTRICTED: The authenticated Facebook account is restricted.";
			case UNKNOWN ->
					"FACEBOOK_AUTH_STATE_UNKNOWN: Facebook authentication state could not be verified.";
			case AUTHENTICATED -> "FACEBOOK_AUTHENTICATED";
		};
	}

	private ScraperExecutionResult failed(String message, FacebookScrapeDiagnostics diagnostics) {
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

	private String cleanPostText(String text) {
		String normalized = FacebookScraperSupport.normalizeText(text);
		if (normalized.isBlank()) {
			return "";
		}
		List<String> lines = normalized.lines()
				.map(String::trim)
				.filter(line -> !line.isBlank())
				.filter(line -> !FacebookScraperSupport.isUiText(line))
				.filter(line -> !dateParser.looksLikeDate(line))
				.toList();
		return String.join("\n", lines);
	}

	private String normalizeProfileUrl(String href) {
		if (href == null || href.isBlank()) {
			return null;
		}
		try {
			String normalized = UrlNormalizer.resolve(FacebookScraperSupport.BASE_URL, href);
			int fragment = normalized.indexOf('#');
			if (fragment >= 0) {
				normalized = normalized.substring(0, fragment);
			}
			int query = normalized.indexOf('?');
			if (query >= 0 && !normalized.substring(0, query).endsWith("/profile.php")) {
				normalized = normalized.substring(0, query);
			}
			return normalized;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private boolean isNonAuthorProfileUrl(String profileUrl) {
		String normalized = profileUrl.toLowerCase(Locale.ROOT);
		return !normalized.contains("facebook.com")
				|| normalized.contains("/posts/")
				|| normalized.contains("/permalink.php")
				|| normalized.contains("/story.php")
				|| normalized.contains("/photo")
				|| normalized.contains("/watch")
				|| normalized.contains("/reel")
				|| normalized.contains("/search")
				|| normalized.contains("/groups/");
	}

	private String authorExternalId(String profileUrl, String fallback) {
		String username = authorUsername(profileUrl, fallback);
		return username == null || username.isBlank() ? fallback : username;
	}

	private String authorUsername(String profileUrl, String fallback) {
		if (profileUrl == null || profileUrl.isBlank()) {
			return fallback;
		}
		try {
			java.net.URI uri = java.net.URI.create(profileUrl);
			String path = uri.getPath();
			if (path == null || path.isBlank() || "/".equals(path)) {
				return fallback;
			}
			if (path.equals("/profile.php") && uri.getQuery() != null) {
				for (String pair : uri.getQuery().split("&")) {
					if (pair.startsWith("id=")) {
						return pair.substring(3);
					}
				}
			}
			return path.replaceFirst("^/", "").split("/")[0];
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private String firstSrcsetUrl(String srcset) {
		if (srcset == null || srcset.isBlank()) {
			return null;
		}
		String firstCandidate = srcset.split(",")[0].trim();
		return firstCandidate.isBlank() ? null : firstCandidate.split("\\s+")[0].trim();
	}

	enum SkipReason {
		SPONSORED(false),
		REEL_DISABLED(false),
		MISSING_POST_URL(true),
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

	record ParseAttempt(FacebookPostCandidate candidate, SkipReason skipReason) {

		static ParseAttempt candidate(FacebookPostCandidate candidate) {
			return new ParseAttempt(candidate, null);
		}

		static ParseAttempt skipped(SkipReason reason) {
			return new ParseAttempt(null, reason);
		}
	}

	private record TimelineCollectionResult(
			List<ScrapedPostDTO> posts,
			boolean visibleContainersFound,
			boolean extractionFailures
	) {
	}

	private record AuthorCandidate(
			String displayName,
			String profileUrl,
			String avatarUrl,
			int score
	) {
	}
}
