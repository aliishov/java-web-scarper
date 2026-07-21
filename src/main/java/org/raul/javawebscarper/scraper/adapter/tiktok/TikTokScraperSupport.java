package org.raul.javawebscarper.scraper.adapter.tiktok;

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

public final class TikTokScraperSupport {

	public static final String SOURCE_CODE = "TIKTOK";
	public static final String BASE_URL = "https://www.tiktok.com";
	private static final Set<String> SUPPORTED_CODES = Set.of("TIKTOK", "TIKTOK_COM", "TT", "BYTE_DANCE_TIKTOK");
	private static final Set<String> NON_CAPTION_TEXT = Set.of(
			"follow", "following", "followers", "likes", "comments", "shares", "share", "sound",
			"original sound", "see more", "more", "log in", "sign up", "for you", "search"
	);

	private TikTokScraperSupport() {
	}

	public static boolean supports(Source source) {
		if (source == null) {
			return false;
		}
		String code = source.getCode() == null ? "" : source.getCode().trim().toUpperCase(Locale.ROOT);
		String baseUrl = source.getBaseUrl() == null ? "" : source.getBaseUrl().trim().toLowerCase(Locale.ROOT);
		return SUPPORTED_CODES.contains(code) || baseUrl.contains("tiktok.com");
	}

	public static List<TikTokPostCandidate> discoverPostCandidates(
			Document document,
			int maxCandidates,
			TikTokScrapeDiagnostics diagnostics
	) {
		LinkedHashMap<String, TikTokPostCandidate> candidates = new LinkedHashMap<>();
		List<Element> links = document.select(TikTokSelectors.VIDEO_LINKS);
		diagnostics.candidateLinksSeen += links.size();
		for (Element link : links) {
			Optional<TikTokPostUrl> parsed = TikTokPostUrlParser.parse(firstNonBlank(link.absUrl("href"), link.attr("href")));
			if (parsed.isEmpty() || parsed.get().shortUrl()) {
				continue;
			}
			TikTokPostUrl postUrl = parsed.get();
			if (candidates.containsKey(postUrl.externalPostId())) {
				diagnostics.duplicateCandidatesSkipped++;
				continue;
			}
			Element card = closestCandidateCard(link);
			candidates.put(postUrl.externalPostId(), new TikTokPostCandidate(
					postUrl.externalPostId(),
					postUrl.canonicalUrl(),
					postUrl.username(),
					extractThumbnailUrl(card == null ? link : card).orElse(null),
					null,
					extractCaptionPreview(card == null ? link : card, postUrl.username()).orElse(null)
			));
			if (candidates.size() >= maxCandidates) {
				break;
			}
		}
		diagnostics.searchCardsSeen += (int) document.select("article, [data-e2e*=search], [data-e2e*=video], div").stream()
				.filter(element -> element.select(TikTokSelectors.VIDEO_LINKS).size() > 0)
				.count();
		diagnostics.uniqueCandidates = Math.max(diagnostics.uniqueCandidates, candidates.size());
		return List.copyOf(candidates.values());
	}

	public static Optional<ScrapedPostDTO> extractPost(
			Document document,
			ScraperExecutionContext context,
			TikTokPostCandidate candidate,
			TikTokDateParser dateParser,
			TikTokScrapeDiagnostics diagnostics,
			TikTokProperties properties
	) {
		Element root = findPostRoot(document).orElse(null);
		if (root == null) {
			diagnostics.postLoadFailures++;
			return Optional.empty();
		}
		if (!properties.isIncludeSponsored() && isSponsored(root)) {
			diagnostics.sponsoredPostsSkipped++;
			return Optional.empty();
		}

		ScrapedAuthorDTO author = extractAuthor(root, candidate).orElse(null);
		TikTokParsedDate parsedDate = extractPostDate(root, document, candidate, dateParser).orElse(null);
		if (parsedDate == null) {
			diagnostics.dateParseFailures++;
		}
		TikTokTextValue caption = extractCaption(root, document, candidate).orElse(null);
		if (caption == null || caption.text().isBlank()) {
			diagnostics.captionEmpty++;
		} else if (caption.source().startsWith("PRIMARY")) {
			diagnostics.captionPrimaryFound++;
		} else {
			diagnostics.captionFallbackUsed++;
		}
		List<ScrapedMediaDTO> media = extractMedia(root, document, diagnostics, properties);
		if (author == null || parsedDate == null || ((caption == null || caption.text().isBlank()) && media.isEmpty())) {
			return Optional.empty();
		}

		String language = resolveLanguage(root, document, context.keyword().getLanguage());
		Map<String, Object> metadata = metadata(context, candidate, diagnostics, caption, parsedDate, media, root);
		return Optional.of(new ScrapedPostDTO(
				candidate.externalPostId(),
				candidate.postUrl(),
				parsedDate.value(),
				author,
				caption == null ? "" : caption.text(),
				language,
				media,
				metadata
		));
	}

	static Optional<Element> findPostRoot(Document document) {
		return document.select(TikTokSelectors.POST_ROOT).stream()
				.max(Comparator.comparingInt(TikTokScraperSupport::postRootScore));
	}

	static Optional<ScrapedAuthorDTO> extractAuthor(Element root, TikTokPostCandidate candidate) {
		String username = candidate.username();
		if (username == null || username.isBlank()) {
			username = root.select(TikTokSelectors.PROFILE_LINKS).stream()
					.map(link -> TikTokPostUrlParser.parse(firstNonBlank(link.absUrl("href"), link.attr("href"))))
					.flatMap(Optional::stream)
					.filter(postUrl -> postUrl.username() != null && !postUrl.username().isBlank())
					.map(TikTokPostUrl::username)
					.findFirst()
					.orElseGet(() -> usernameFromProfileLink(root).orElse(""));
		}
		if (username.isBlank()) {
			return Optional.empty();
		}
		String normalizedUsername = username.replace("@", "").trim();
		String displayName = extractDisplayName(root, normalizedUsername).orElse(normalizedUsername);
		return Optional.of(new ScrapedAuthorDTO(
				normalizedUsername.toLowerCase(Locale.ROOT),
				normalizedUsername,
				displayName,
				BASE_URL + "/@" + normalizedUsername,
				extractAuthorAvatar(root, normalizedUsername).orElse(null)
		));
	}

	static Optional<TikTokTextValue> extractCaption(Element root, Document document, TikTokPostCandidate candidate) {
		for (Element element : root.select(TikTokSelectors.CAPTION_CANDIDATES)) {
			if (isInside(element, "aside, [data-e2e*=comment], [class*=comment], [data-e2e*=suggest], [class*=suggest], footer")) {
				continue;
			}
			String text = cleanTikTokCaption(element.text(), candidate.username());
			if (isCaptionText(text, candidate.username())) {
				return Optional.of(new TikTokTextValue(text, "PRIMARY_DOM"));
			}
		}
		for (String selector : List.of("script[type='application/ld+json']", "meta[property='og:description']", "meta[property='og:title']")) {
			for (Element element : document.select(selector)) {
				String raw = element.normalName().equals("meta") ? element.attr("content") : element.data();
				String text = cleanTikTokCaption(raw, candidate.username());
				if (isCaptionText(text, candidate.username())) {
					return Optional.of(new TikTokTextValue(text, selector.contains("ld+json") ? "STRUCTURED_DATA" : "META"));
				}
			}
		}
		String title = cleanTikTokCaption(document.title(), candidate.username());
		if (isCaptionText(title, candidate.username())) {
			return Optional.of(new TikTokTextValue(title, "DOCUMENT_TITLE"));
		}
		String preview = cleanTikTokCaption(candidate.captionPreview(), candidate.username());
		if (isCaptionText(preview, candidate.username())) {
			return Optional.of(new TikTokTextValue(preview, "SEARCH_CARD_PREVIEW"));
		}
		return Optional.empty();
	}

	static List<ScrapedMediaDTO> extractMedia(
			Element root,
			Document document,
			TikTokScrapeDiagnostics diagnostics,
			TikTokProperties properties
	) {
		List<ScrapedMediaDTO> media = new ArrayList<>();
		Set<String> visited = new LinkedHashSet<>();
		int position = 0;
		for (Element video : root.select("video")) {
			diagnostics.videosDetected++;
			boolean videoUrlCollected = false;
			for (String value : List.of(
					video.attr("src"),
					video.attr("data-src"),
					video.attr("currentSrc")
			)) {
				Optional<String> url = normalizedHttpsUrl(value);
				if (url.isPresent() && visited.add(url.get())) {
					media.add(new ScrapedMediaDTO(url.get(), MediaType.VIDEO, position++));
					diagnostics.videoUrlsCollected++;
					videoUrlCollected = true;
				}
			}
			for (Element source : video.select("source[src]")) {
				Optional<String> url = normalizedHttpsUrl(firstNonBlank(source.absUrl("src"), source.attr("src")));
				if (url.isPresent() && visited.add(url.get())) {
					media.add(new ScrapedMediaDTO(url.get(), MediaType.VIDEO, position++));
					diagnostics.videoUrlsCollected++;
					videoUrlCollected = true;
				}
			}
			String rawVideo = firstNonBlank(video.attr("src"), video.attr("data-src"));
			if (!videoUrlCollected && (rawVideo.startsWith("blob:") || rawVideo.startsWith("data:") || root.select("video").size() > 0)) {
				diagnostics.videoUrlUnavailable++;
			}
			Optional<String> poster = normalizedHttpsUrl(firstNonBlank(video.absUrl("poster"), video.attr("poster")));
			if (poster.isPresent() && visited.add(poster.get())) {
				media.add(new ScrapedMediaDTO(poster.get(), MediaType.IMAGE, position++));
				diagnostics.imagesCollected++;
			}
		}

		if (properties.isIncludePhotoPosts()) {
			for (Element image : root.select("img[src], img[data-src]")) {
				if (isIgnoredImage(image)) {
					continue;
				}
				Optional<String> imageUrl = bestImageUrl(image);
				if (imageUrl.isPresent() && visited.add(imageUrl.get())) {
					media.add(new ScrapedMediaDTO(imageUrl.get(), MediaType.IMAGE, position++));
					diagnostics.imagesCollected++;
				}
			}
		}

		if (media.isEmpty()) {
			for (Element meta : document.select("meta[property='og:image'], meta[property='og:video'], meta[property='og:video:url'], meta[name='twitter:player:stream']")) {
				Optional<String> url = normalizedHttpsUrl(meta.attr("content"));
				if (url.isPresent() && visited.add(url.get())) {
					MediaType type = meta.attr("property").contains("video") || meta.attr("name").contains("stream")
							? MediaType.VIDEO
							: MediaType.IMAGE;
					media.add(new ScrapedMediaDTO(url.get(), type, position++));
					if (type == MediaType.VIDEO) {
						diagnostics.videoUrlsCollected++;
					} else {
						diagnostics.imagesCollected++;
					}
				}
			}
		}
		if (root.select("video").isEmpty() && media.size() > 1) {
			diagnostics.photoPostsCollected++;
		}
		return List.copyOf(media);
	}

	private static Optional<TikTokParsedDate> extractPostDate(
			Element root,
			Document document,
			TikTokPostCandidate candidate,
			TikTokDateParser dateParser
	) {
		for (Element element : root.select(TikTokSelectors.TIME)) {
			for (String value : List.of(
					element.attr("datetime"),
					element.attr("title"),
					element.attr("aria-label"),
					element.attr("data-time"),
					element.attr("data-timestamp"),
					element.text()
			)) {
				Optional<TikTokParsedDate> parsed = dateParser.parseWithSource(value);
				if (parsed.isPresent()) {
					return parsed;
				}
			}
		}
		for (Element meta : document.select("meta[property='article:published_time'], meta[itemprop='uploadDate'], meta[name='publishdate']")) {
			Optional<TikTokParsedDate> parsed = dateParser.parseWithSource(meta.attr("content"));
			if (parsed.isPresent()) {
				return parsed;
			}
		}
		if (candidate.cardDate() != null) {
			return Optional.of(new TikTokParsedDate(candidate.cardDate(), "SEARCH_CARD"));
		}
		return Optional.empty();
	}

	private static Map<String, Object> metadata(
			ScraperExecutionContext context,
			TikTokPostCandidate candidate,
			TikTokScrapeDiagnostics diagnostics,
			TikTokTextValue caption,
			TikTokParsedDate date,
			List<ScrapedMediaDTO> media,
			Element root
	) {
		boolean hasVideo = root.select("video").size() > 0 || media.stream().anyMatch(item -> item.mediaType() == MediaType.VIDEO);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		metadata.put("searchMode", diagnostics.searchMode == null ? TikTokSearchMode.AUTO.name() : diagnostics.searchMode.name());
		metadata.put("postType", hasVideo ? TikTokPostType.VIDEO.name() : TikTokPostType.PHOTO.name());
		metadata.put("hasVideo", hasVideo);
		metadata.put("videoUrlUnavailable", diagnostics.videoUrlUnavailable > 0);
		metadata.put("isCarousel", media.size() > 1);
		metadata.put("carouselSize", media.size());
		metadata.put("dateSource", date.source());
		metadata.put("captionSource", caption == null ? "NONE" : caption.source());
		metadata.put("anonymousSession", diagnostics.anonymousSession);
		if (candidate.thumbnailUrl() != null && !candidate.thumbnailUrl().isBlank()) {
			metadata.put("thumbnailUrl", candidate.thumbnailUrl());
		}
		extractSoundMetadata(root).forEach(metadata::put);
		return metadata;
	}

	private static Map<String, Object> extractSoundMetadata(Element root) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		Element soundLink = root.selectFirst("a[href*='/music/'], a[href*='/sound/']");
		if (soundLink == null) {
			return metadata;
		}
		String text = cleanText(soundLink.text());
		if (!text.isBlank()) {
			metadata.put("soundTitle", text);
		}
		String href = firstNonBlank(soundLink.absUrl("href"), soundLink.attr("href"));
		if (!href.isBlank() && href.startsWith("https://")) {
			metadata.put("soundUrl", href);
		}
		return metadata;
	}

	private static Element closestCandidateCard(Element link) {
		for (String selector : List.of("article", "[data-e2e*=search], [data-e2e*=video], div")) {
			Element closest = link.closest(selector);
			if (closest != null) {
				return closest;
			}
		}
		return link;
	}

	private static int postRootScore(Element element) {
		int score = 0;
		score += element.select(TikTokSelectors.VIDEO_LINKS).isEmpty() ? 0 : 10;
		score += element.select("video").isEmpty() ? 0 : 8;
		score += element.select(TikTokSelectors.CAPTION_CANDIDATES).isEmpty() ? 0 : 6;
		score += element.select(TikTokSelectors.PROFILE_LINKS).isEmpty() ? 0 : 5;
		score += element.select(TikTokSelectors.TIME).isEmpty() ? 0 : 3;
		return score;
	}

	private static boolean isSponsored(Element root) {
		String text = cleanText(root.text()).toLowerCase(Locale.ROOT);
		return text.contains("sponsored")
				|| text.contains("sponsorlu")
				|| text.contains("реклама")
				|| text.matches(".*\\bad\\b.*");
	}

	private static Optional<String> usernameFromProfileLink(Element root) {
		for (Element link : root.select(TikTokSelectors.PROFILE_LINKS)) {
			String href = firstNonBlank(link.absUrl("href"), link.attr("href"));
			String path = href.replace(BASE_URL, "");
			int at = path.indexOf("/@");
			if (at >= 0) {
				String username = path.substring(at + 2).split("[/?#]")[0];
				if (!username.isBlank()) {
					return Optional.of(username);
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<String> extractDisplayName(Element root, String username) {
		for (Element element : root.select("[data-e2e*=browse-username], [data-e2e*=video-author], a[href^='/@'], a[href*='tiktok.com/@']")) {
			String text = cleanText(element.text()).replace("@", "");
			if (!text.isBlank() && !text.equalsIgnoreCase(username) && text.length() <= 80) {
				return Optional.of(text);
			}
		}
		return Optional.empty();
	}

	private static Optional<String> extractAuthorAvatar(Element root, String username) {
		String lowerUsername = username == null ? "" : username.toLowerCase(Locale.ROOT);
		for (Element image : root.select("header img[src], a[href^='/@'] img[src], a[href*='tiktok.com/@'] img[src], img[src][alt]")) {
			String alt = image.attr("alt").toLowerCase(Locale.ROOT);
			String src = firstNonBlank(image.attr("src"), image.attr("data-src")).toLowerCase(Locale.ROOT);
			if ((alt.contains("avatar") || alt.contains("profile") || (!lowerUsername.isBlank() && alt.contains(lowerUsername)))
					&& !src.startsWith("blob:")
					&& !src.startsWith("data:")
					&& !src.contains("logo")) {
				Optional<String> avatarUrl = bestImageUrl(image);
				if (avatarUrl.isPresent()) {
					return avatarUrl;
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<String> extractCaptionPreview(Element card, String username) {
		for (Element element : card.select("[data-e2e*='search-card-video-caption'], [data-e2e*='video-desc'], [dir='auto'], h1")) {
			String text = cleanTikTokCaption(element.text(), username);
			if (isCaptionText(text, username)) {
				return Optional.of(text);
			}
		}
		return Optional.empty();
	}

	private static Optional<String> extractThumbnailUrl(Element element) {
		for (Element image : element.select("img[src], img[data-src]")) {
			if (!isIgnoredImage(image)) {
				Optional<String> url = bestImageUrl(image);
				if (url.isPresent()) {
					return url;
				}
			}
		}
		return Optional.empty();
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
		return normalizedHttpsUrl(firstNonBlank(image.absUrl("src"), image.attr("src"), image.absUrl("data-src"), image.attr("data-src")));
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
		if (value.startsWith("https://p16-sign") || value.contains("tiktokcdn") || value.contains("muscdn") || value.contains("tiktok")) {
			return Optional.of(value);
		}
		return Optional.of(value);
	}

	private static boolean isIgnoredImage(Element image) {
		String alt = image.attr("alt").toLowerCase(Locale.ROOT);
		String src = firstNonBlank(image.attr("src"), image.attr("data-src")).toLowerCase(Locale.ROOT);
		return isInside(image, "header [data-e2e*=comment], aside, [data-e2e*=comment], [class*=comment], [data-e2e*=suggest], [class*=suggest]")
				|| alt.contains("avatar")
				|| alt.contains("profile")
				|| alt.contains("emoji")
				|| alt.contains("logo")
				|| alt.contains("comment")
				|| src.startsWith("data:")
				|| src.startsWith("blob:")
				|| src.contains("emoji")
				|| src.contains("logo");
	}

	private static boolean isCaptionText(String text, String username) {
		String normalized = cleanText(text);
		if (normalized.isBlank()) {
			return false;
		}
		String lower = normalized.toLowerCase(Locale.ROOT);
		String lowerUsername = username == null ? "" : username.toLowerCase(Locale.ROOT).replace("@", "");
		return !NON_CAPTION_TEXT.contains(lower)
				&& !lower.equals(lowerUsername)
				&& !lower.equals("@" + lowerUsername)
				&& !lower.matches("^\\d+[,.]?\\d*\\s*(likes?|comments?|shares?|views?)$")
				&& !lower.matches("^(like|comment|share|save)\\s+\\d+.*$");
	}

	static String cleanTikTokCaption(String value, String username) {
		String raw = cleanText(value);
		if (raw.isBlank()) {
			return "";
		}
		String text = raw.replaceAll("(?i)^tiktok video from [^:]+:\\s*[\\u201c\\\"](.+?)[\\u201d\\\"]\\.?$", "$1")
				.replaceAll("(?i)\\s*(?:[|\\u00b7]|\\s-\\s)\\s*TikTok(?:\\s*[-|].*)?$", "")
				.replaceAll("\\s+", " ")
				.trim();
		String identity = username == null ? "" : username.replace("@", "").trim();
		if (!identity.isBlank()) {
			text = text.replaceFirst("^@" + java.util.regex.Pattern.quote(identity) + "\\s+", "").trim();
			text = text.replaceFirst("^" + java.util.regex.Pattern.quote(identity) + "\\s+", "").trim();
		}
		String lower = text.toLowerCase(Locale.ROOT);
		if (lower.startsWith("tiktok") || lower.startsWith("make your day")) {
			return "";
		}
		return text;
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

	private static boolean isInside(Element element, String selector) {
		return element.parents().stream().anyMatch(parent -> parent.is(selector));
	}

	private static String cleanText(String value) {
		return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return "";
	}
}
