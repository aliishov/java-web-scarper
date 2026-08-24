package org.raul.javawebscarper.scraper.adapter.instagram;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;

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

public final class InstagramScraperSupport {

	public static final String SOURCE_CODE = "INSTAGRAM";
	public static final String BASE_URL = "https://www.instagram.com";
	private static final Set<String> SUPPORTED_CODES = Set.of("INSTAGRAM", "INSTAGRAM_COM", "IG", "META_INSTAGRAM");
	private static final Set<String> RESERVED_PROFILE_PATHS = Set.of(
			"p", "reel", "reels", "tv", "explore", "accounts", "direct", "stories", "tags", "about", "developer"
	);
	private static final Set<String> NON_CAPTION_TEXT = Set.of(
			"like", "reply", "view replies", "view more comments", "see translation", "more",
			"sponsored", "suggested posts", "likes", "comments"
	);

	private InstagramScraperSupport() {
	}

	public static boolean supports(Source source) {
		if (source == null) {
			return false;
		}
		String code = source.getCode() == null ? "" : source.getCode().trim().toUpperCase(Locale.ROOT);
		String baseUrl = source.getBaseUrl() == null ? "" : source.getBaseUrl().trim().toLowerCase(Locale.ROOT);
		return SUPPORTED_CODES.contains(code) || baseUrl.contains("instagram.com");
	}

	public static List<InstagramPostCandidate> discoverPostCandidates(
			Document document,
			boolean includeReels,
			int maxCandidates,
			InstagramScrapeDiagnostics diagnostics
	) {
		LinkedHashMap<String, InstagramPostCandidate> candidates = new LinkedHashMap<>();
		for (Element link : document.select(InstagramSelectors.GRID_POST_LINKS)) {
			String href = firstNonBlank(link.absUrl("href"), link.attr("href"));
			Optional<InstagramPostUrl> parsed = InstagramPostUrlParser.parse(href);
			if (parsed.isEmpty()) {
				continue;
			}
			InstagramPostUrl postUrl = parsed.get();
			if (postUrl.isReel() && !includeReels) {
				diagnostics.reelsSkipped++;
				continue;
			}
			String key = postUrl.externalPostId();
			if (candidates.containsKey(key)) {
				diagnostics.duplicateCandidatesSkipped++;
				continue;
			}
			candidates.put(key, new InstagramPostCandidate(
					postUrl.externalPostId(),
					postUrl.canonicalUrl(),
					postUrl.type(),
					extractThumbnailUrl(link).orElse(null)
			));
			if (candidates.size() >= maxCandidates) {
				break;
			}
		}
		diagnostics.postGridLinksSeen += document.select(InstagramSelectors.GRID_POST_LINKS).size();
		diagnostics.uniquePostCandidates = Math.max(diagnostics.uniquePostCandidates, candidates.size());
		return List.copyOf(candidates.values());
	}

	public static Optional<ScrapedPostDTO> extractPost(
			Document document,
			ScraperExecutionContext context,
			InstagramPostCandidate candidate,
			InstagramDateParser dateParser,
			InstagramScrapeDiagnostics diagnostics,
			InstagramProperties properties
	) {
		Element root = findPostRoot(document).orElse(null);
		if (root == null) {
			diagnostics.extractionFailures++;
			return Optional.empty();
		}
		if (!properties.isIncludeSponsored() && isSponsored(root)) {
			diagnostics.sponsoredPostsSkipped++;
			return Optional.empty();
		}
		ScrapedAuthorDTO author = extractAuthor(root).orElse(null);
		OffsetDateTime postDate = extractPostDate(root, dateParser).orElse(null);
		if (postDate == null) {
			diagnostics.dateParseFailures++;
		}
		String text = extractCaption(root, author).orElse("");
		List<ScrapedMediaDTO> media = extractMedia(root, diagnostics);
		if (text.isBlank() && !media.isEmpty()) {
			diagnostics.mediaOnlyPostsCollected++;
		}
		if (author == null || postDate == null || (text.isBlank() && media.isEmpty())) {
			diagnostics.extractionFailures++;
			return Optional.empty();
		}
		String language = resolveLanguage(root, document, context.keyword().getLanguage());
		Map<String, Object> metadata = metadata(context, candidate, root, media, diagnostics);
		return Optional.of(new ScrapedPostDTO(
				candidate.externalPostId(),
				candidate.postUrl(),
				postDate,
				author,
				text,
				language,
				media,
				metadata
		));
	}

	static Optional<Element> findPostRoot(Document document) {
		return document.select(InstagramSelectors.POST_ROOT).stream()
				.max(Comparator.comparingInt(InstagramScraperSupport::postRootScore));
	}

	static Optional<ScrapedAuthorDTO> extractAuthor(Element root) {
		List<Element> scopes = new ArrayList<>();
		Element header = root.selectFirst("header");
		if (header != null) {
			scopes.add(header);
		}
		scopes.add(root);
		for (Element scope : scopes) {
			for (Element link : scope.select(InstagramSelectors.PROFILE_LINKS)) {
				Optional<String> username = usernameFromProfileUrl(firstNonBlank(link.absUrl("href"), link.attr("href")));
				if (username.isEmpty()) {
					continue;
				}
				String normalizedUsername = username.get().toLowerCase(Locale.ROOT);
				String displayName = cleanText(link.text());
				if (displayName.isBlank()) {
					displayName = username.get();
				}
				return Optional.of(new ScrapedAuthorDTO(
						normalizedUsername,
						username.get(),
						displayName,
						BASE_URL + "/" + username.get() + "/",
						extractHeaderAvatar(root).orElse(null)
				));
			}
		}
		return Optional.empty();
	}

	static Optional<String> extractCaption(Element root, ScrapedAuthorDTO author) {
		String username = author == null ? "" : author.username();
		String displayName = author == null ? "" : author.displayName();
		for (Element element : root.select(InstagramSelectors.CAPTION_CANDIDATES)) {
			if (isInside(element, "time") || isInside(element, "header")) {
				continue;
			}
			String text = cleanText(element.text());
			text = removeLeadingIdentity(text, username);
			text = removeLeadingIdentity(text, displayName);
			if (isCaptionText(text, username, displayName)) {
				return Optional.of(text);
			}
		}
		return Optional.empty();
	}

	static List<ScrapedMediaDTO> extractMedia(Element root, InstagramScrapeDiagnostics diagnostics) {
		List<ScrapedMediaDTO> media = new ArrayList<>();
		Set<String> visited = new LinkedHashSet<>();
		int position = 0;
		for (Element element : root.select(InstagramSelectors.MEDIA)) {
			if (element.normalName().equals("img")) {
				if (isIgnoredImage(element)) {
					continue;
				}
				Optional<String> imageUrl = bestImageUrl(element);
				if (imageUrl.isPresent() && visited.add(imageUrl.get())) {
					media.add(new ScrapedMediaDTO(imageUrl.get(), MediaType.IMAGE, position++));
					diagnostics.imagesCollected++;
				}
			}
			if (element.normalName().equals("video")) {
				Optional<String> videoUrl = normalizedHttpsUrl(firstNonBlank(element.attr("src"), element.attr("data-src")));
				if (videoUrl.isPresent() && visited.add(videoUrl.get())) {
					media.add(new ScrapedMediaDTO(videoUrl.get(), MediaType.VIDEO, position++));
				} else if (firstNonBlank(element.attr("src"), element.attr("data-src")).startsWith("blob:")) {
					diagnostics.videosDetected++;
				}
				Optional<String> posterUrl = normalizedHttpsUrl(element.attr("poster"));
				if (posterUrl.isPresent() && visited.add(posterUrl.get())) {
					media.add(new ScrapedMediaDTO(posterUrl.get(), MediaType.IMAGE, position++));
					diagnostics.imagesCollected++;
					diagnostics.videosDetected++;
				}
			}
		}
		if (media.size() > 1) {
			diagnostics.carouselsDetected++;
			diagnostics.carouselItemsCollected += media.size();
		}
		return List.copyOf(media);
	}

	private static Optional<OffsetDateTime> extractPostDate(Element root, InstagramDateParser dateParser) {
		for (Element time : root.select(InstagramSelectors.TIME)) {
			for (String value : List.of(
					time.attr("datetime"),
					time.attr("title"),
					time.attr("aria-label"),
					time.text()
			)) {
				Optional<OffsetDateTime> parsed = dateParser.parse(value);
				if (parsed.isPresent()) {
					return parsed;
				}
			}
		}
		return Optional.empty();
	}

	private static Map<String, Object> metadata(
			ScraperExecutionContext context,
			InstagramPostCandidate candidate,
			Element root,
			List<ScrapedMediaDTO> media,
			InstagramScrapeDiagnostics diagnostics
	) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		metadata.put("searchMode", diagnostics.searchMode == null ? InstagramSearchMode.AUTO.name() : diagnostics.searchMode.name());
		metadata.put("searchResultType", InstagramSearchResultType.POST.name());
		metadata.put("hashtagFallbackUsed", diagnostics.hashtagFallbackUsed);
		metadata.put("postType", candidate.postType().name());
		metadata.put("isCarousel", media.size() > 1);
		metadata.put("carouselSize", media.size());
		metadata.put("hasVideo", media.stream().anyMatch(mediaItem -> mediaItem.mediaType() == MediaType.VIDEO) || root.select("video").size() > 0);
		metadata.put("taggedUsernames", List.of());
		metadata.put("collaboratorUsernames", collaboratorUsernames(root));
		if (candidate.thumbnailUrl() != null && !candidate.thumbnailUrl().isBlank()) {
			metadata.put("thumbnailUrl", candidate.thumbnailUrl());
		}
		return metadata;
	}

	private static List<String> collaboratorUsernames(Element root) {
		Set<String> usernames = new LinkedHashSet<>();
		for (Element headerLink : root.select("header a[href]")) {
			usernameFromProfileUrl(firstNonBlank(headerLink.absUrl("href"), headerLink.attr("href"))).ifPresent(usernames::add);
		}
		if (!usernames.isEmpty()) {
			usernames.remove(usernames.iterator().next());
		}
		return List.copyOf(usernames);
	}

	private static String resolveLanguage(Element root, Document document, Language fallback) {
		String raw = firstNonBlank(root.attr("lang"), document.selectFirst("[lang]") == null ? "" : document.selectFirst("[lang]").attr("lang"));
		String normalized = raw.toLowerCase(Locale.ROOT);
		if (normalized.startsWith("az")) {
			return "az";
		}
		if (normalized.startsWith("ru")) {
			return "ru";
		}
		if (normalized.startsWith("en")) {
			return "en";
		}
		if (normalized.startsWith("tr")) {
			return "tr";
		}
		Language language = fallback == null ? Language.AZ : fallback;
		return language.name().toLowerCase(Locale.ROOT);
	}

	private static int postRootScore(Element element) {
		int score = 0;
		if (isBroadPostContainer(element) && !element.select("article").isEmpty()) {
			score -= 100;
		}
		score += element.select(InstagramSelectors.TIME).isEmpty() ? 0 : 5;
		score += element.select("header a[href], a[role='link'][href]").isEmpty() ? 0 : 3;
		score += element.select("img[src], video").isEmpty() ? 0 : 2;
		score += element.select("h1[dir='auto'], [data-testid='post-comment-root']").isEmpty() ? 0 : 2;
		return score;
	}

	private static boolean isBroadPostContainer(Element element) {
		return element.normalName().equals("main") || "main".equalsIgnoreCase(element.attr("role"));
	}

	private static boolean isSponsored(Element root) {
		String text = cleanText(root.text()).toLowerCase(Locale.ROOT);
		return text.contains("sponsored") || text.contains("sponsorlu") || text.contains("реклама");
	}

	private static Optional<String> usernameFromProfileUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		try {
			java.net.URI uri = java.net.URI.create(rawUrl.startsWith("/") ? BASE_URL + rawUrl : rawUrl);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (!host.equals("instagram.com") && !host.equals("www.instagram.com")) {
				return Optional.empty();
			}
			String[] segments = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
			if (segments.length != 2 || segments[1].isBlank()) {
				return Optional.empty();
			}
			String username = segments[1];
			if (RESERVED_PROFILE_PATHS.contains(username.toLowerCase(Locale.ROOT))) {
				return Optional.empty();
			}
			return Optional.of(username.replace("@", ""));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private static Optional<String> extractHeaderAvatar(Element root) {
		Element image = root.selectFirst("header img[src]");
		if (image == null) {
			return Optional.empty();
		}
		return normalizedHttpsUrl(firstNonBlank(image.attr("src"), image.attr("data-src")));
	}

	private static Optional<String> extractThumbnailUrl(Element link) {
		Element image = link.selectFirst("img[src], img[data-src]");
		if (image == null) {
			return Optional.empty();
		}
		return bestImageUrl(image);
	}

	private static boolean isIgnoredImage(Element image) {
		String alt = image.attr("alt").toLowerCase(Locale.ROOT);
		String src = firstNonBlank(image.attr("src"), image.attr("data-src")).toLowerCase(Locale.ROOT);
		return isInside(image, "header")
				|| alt.contains("profile picture")
				|| alt.contains("avatar")
				|| alt.contains("emoji")
				|| alt.contains("logo")
				|| src.startsWith("data:")
				|| src.startsWith("blob:")
				|| src.contains("emoji");
	}

	private static Optional<String> bestImageUrl(Element image) {
		String srcset = image.attr("srcset");
		if (srcset != null && !srcset.isBlank()) {
			String[] candidates = srcset.split(",");
			for (int index = candidates.length - 1; index >= 0; index--) {
				String candidate = candidates[index].trim().split("\\s+")[0];
				Optional<String> normalized = normalizedHttpsUrl(candidate);
				if (normalized.isPresent()) {
					return normalized;
				}
			}
		}
		return normalizedHttpsUrl(firstNonBlank(image.attr("src"), image.attr("data-src")));
	}

	private static Optional<String> normalizedHttpsUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		String value = rawUrl.trim();
		if (value.startsWith("//")) {
			value = "https:" + value;
		}
		if (!value.startsWith("https://")) {
			return Optional.empty();
		}
		return Optional.of(value);
	}

	private static boolean isCaptionText(String text, String username, String displayName) {
		String normalized = cleanText(text);
		if (normalized.isBlank()) {
			return false;
		}
		String lower = normalized.toLowerCase(Locale.ROOT);
		return !NON_CAPTION_TEXT.contains(lower)
				&& !normalized.equals(username)
				&& !normalized.equals(displayName)
				&& !lower.matches("^\\d+[,.]?\\d*\\s+likes?$")
				&& !lower.matches("^view\\s+\\d+\\s+comments?$");
	}

	private static String removeLeadingIdentity(String text, String identity) {
		if (text == null || text.isBlank() || identity == null || identity.isBlank()) {
			return text == null ? "" : text.trim();
		}
		String normalized = text.trim();
		String safeIdentity = identity.trim();
		if (normalized.equals(safeIdentity)) {
			return "";
		}
		if (normalized.startsWith(safeIdentity + " ")) {
			return normalized.substring(safeIdentity.length()).trim();
		}
		if (normalized.startsWith("@" + safeIdentity + " ")) {
			return normalized.substring(safeIdentity.length() + 1).trim();
		}
		return normalized;
	}

	private static boolean isInside(Element element, String tagName) {
		return element.parents().stream().anyMatch(parent -> parent.normalName().equals(tagName));
	}

	private static String cleanText(String value) {
		return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
	}

	private static String firstNonBlank(String first, String second) {
		return first == null || first.isBlank() ? (second == null ? "" : second.trim()) : first.trim();
	}
}
