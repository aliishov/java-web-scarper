package org.raul.javawebscarper.scraper.adapter.threads;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedMediaDTO;
import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.model.enumerated.MediaType;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.support.KeywordTextMatcher;

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

public final class ThreadsScraperSupport {

	public static final String SOURCE_CODE = "THREADS";
	public static final String BASE_URL = "https://www.threads.com";
	private static final Set<String> SUPPORTED_CODES = Set.of("THREADS", "THREADS_COM", "THREADS_NET", "META_THREADS");
	private static final Set<String> UI_TEXT = Set.of(
			"like", "reply", "repost", "share", "follow", "following", "translate",
			"see more", "more", "views", "replies", "log in", "sign up"
	);

	private ThreadsScraperSupport() {
	}

	public static boolean supports(Source source) {
		if (source == null) {
			return false;
		}
		String code = source.getCode() == null ? "" : source.getCode().trim().toUpperCase(Locale.ROOT);
		String baseUrl = source.getBaseUrl() == null ? "" : source.getBaseUrl().trim().toLowerCase(Locale.ROOT);
		return SUPPORTED_CODES.contains(code) || baseUrl.contains("threads.com") || baseUrl.contains("threads.net");
	}

	static List<ThreadsPostCandidate> discoverCandidates(
			Document document,
			int maxCandidates,
			ThreadsScrapeDiagnostics diagnostics
	) {
		Map<String, ThreadsPostCandidate> candidates = new LinkedHashMap<>();
		List<Element> links = document.select(ThreadsSelectors.POST_LINKS);
		diagnostics.postLinksSeen += links.size();
		for (Element link : links) {
			String href = firstNonBlank(link.absUrl("href"), link.attr("href"));
			Optional<ThreadsPostUrl> parsed = ThreadsPostUrlParser.parse(href);
			if (parsed.isEmpty()) {
				continue;
			}
			ThreadsPostUrl postUrl = parsed.get();
			if (candidates.containsKey(postUrl.externalPostId())) {
				diagnostics.duplicateCandidates++;
				continue;
			}
			candidates.put(postUrl.externalPostId(), new ThreadsPostCandidate(
					postUrl.externalPostId(),
					postUrl.username(),
					postUrl.canonicalUrl()
			));
			if (candidates.size() >= maxCandidates) {
				break;
			}
		}
		diagnostics.uniqueCandidates = Math.max(diagnostics.uniqueCandidates, candidates.size());
		return List.copyOf(candidates.values());
	}

	static Optional<ScrapedPostDTO> extractPost(
			Document document,
			ScraperExecutionContext context,
			ThreadsPostCandidate candidate,
			ThreadsDateParser dateParser,
			ThreadsProperties properties,
			ThreadsScrapeDiagnostics diagnostics
	) {
		Element root = findRoot(document, candidate.externalPostId()).orElse(null);
		if (root == null) {
			diagnostics.extractionFailures++;
			return Optional.empty();
		}
		if (!properties.isIncludeReplies() && isReply(root)) {
			diagnostics.repliesSkipped++;
			return Optional.empty();
		}
		if (!properties.isIncludeReposts() && isRepost(root)) {
			diagnostics.repostsSkipped++;
			return Optional.empty();
		}
		ScrapedAuthorDTO author = extractAuthor(root, candidate).orElse(null);
		OffsetDateTime postDate = extractDate(root, dateParser).orElse(null);
		if (postDate == null) {
			diagnostics.dateParseFailures++;
		}
		String text = extractText(root, author).orElse("");
		List<ScrapedMediaDTO> media = extractMedia(root);
		if (author == null || postDate == null || (text.isBlank() && media.isEmpty())) {
			diagnostics.extractionFailures++;
			return Optional.empty();
		}
		if (KeywordTextMatcher.findMatch(text, context.keyword().getWord()).isEmpty()) {
			diagnostics.keywordMismatchSkipped++;
			return Optional.empty();
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		metadata.put("isReply", isReply(root));
		metadata.put("isRepost", isRepost(root));
		metadata.put("diagnostics", diagnostics.toMetadata());
		return Optional.of(new ScrapedPostDTO(
				candidate.externalPostId(),
				candidate.postUrl(),
				postDate,
				author,
				text,
				resolveLanguage(root, document, context.keyword().getLanguage()),
				media,
				metadata
		));
	}

	private static Optional<Element> findRoot(Document document, String postId) {
		for (Element link : document.select(ThreadsSelectors.POST_LINKS)) {
			Optional<ThreadsPostUrl> parsed = ThreadsPostUrlParser.parse(firstNonBlank(link.absUrl("href"), link.attr("href")));
			if (parsed.isPresent() && parsed.get().externalPostId().equals(postId)) {
				Element article = link.closest("article");
				if (article != null) {
					return Optional.of(article);
				}
				Element bestAncestor = null;
				int bestScore = Integer.MIN_VALUE;
				Element ancestor = link;
				for (int depth = 0; ancestor != null && depth < 10; depth++, ancestor = ancestor.parent()) {
					if (ancestor.normalName().equals("main") || ancestor.normalName().equals("body")
							|| ancestor.normalName().equals("html")) {
						break;
					}
					int score = rootScore(ancestor);
					if (score > bestScore) {
						bestScore = score;
						bestAncestor = ancestor;
					}
				}
				if (bestAncestor != null && bestScore > 0) {
					return Optional.of(bestAncestor);
				}
			}
		}
		return document.select(ThreadsSelectors.POST_ROOT).stream()
				.max(Comparator.comparingInt(ThreadsScraperSupport::rootScore));
	}

	private static Optional<ScrapedAuthorDTO> extractAuthor(Element root, ThreadsPostCandidate candidate) {
		for (Element link : root.select(ThreadsSelectors.PROFILE_LINKS)) {
			String href = firstNonBlank(link.absUrl("href"), link.attr("href"));
			String username = usernameFromProfileUrl(href).orElse("");
			if (username.isBlank()) {
				continue;
			}
			String displayName = cleanText(link.text());
			return Optional.of(new ScrapedAuthorDTO(
					username.toLowerCase(Locale.ROOT),
					username,
					displayName.isBlank() ? username : displayName,
					BASE_URL + "/@" + username,
					headerAvatar(root).orElse(null)
			));
		}
		if (candidate.username() == null || candidate.username().isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new ScrapedAuthorDTO(
				candidate.username().toLowerCase(Locale.ROOT),
				candidate.username(),
				candidate.username(),
				BASE_URL + "/@" + candidate.username(),
				headerAvatar(root).orElse(null)
		));
	}

	private static Optional<String> extractText(Element root, ScrapedAuthorDTO author) {
		String username = author == null ? "" : author.username();
		return root.select(ThreadsSelectors.TEXT).stream()
				.filter(element -> element.closest("button") == null && element.closest("time") == null)
				.map(Element::text)
				.map(ThreadsScraperSupport::cleanText)
				.filter(text -> isPostText(text, username))
				.max(Comparator.comparingInt(String::length));
	}

	private static Optional<OffsetDateTime> extractDate(Element root, ThreadsDateParser dateParser) {
		for (Element time : root.select(ThreadsSelectors.TIME)) {
			for (String value : List.of(time.attr("datetime"), time.attr("title"), time.text())) {
				Optional<OffsetDateTime> parsed = dateParser.parse(value);
				if (parsed.isPresent()) {
					return parsed;
				}
			}
		}
		return Optional.empty();
	}

	private static List<ScrapedMediaDTO> extractMedia(Element root) {
		List<ScrapedMediaDTO> media = new ArrayList<>();
		Set<String> visited = new LinkedHashSet<>();
		int position = 0;
		for (Element element : root.select(ThreadsSelectors.MEDIA)) {
			if (isProfileImage(element)) {
				continue;
			}
			if (element.normalName().equals("img")) {
				String url = firstNonBlank(element.attr("src"), element.attr("data-src"));
				if (isHttps(url) && visited.add(url)) {
					media.add(new ScrapedMediaDTO(url, MediaType.IMAGE, position++));
				}
			} else {
				String url = firstNonBlank(element.attr("src"), element.attr("poster"));
				if (isHttps(url) && visited.add(url)) {
					MediaType type = element.hasAttr("poster") && !element.hasAttr("src") ? MediaType.IMAGE : MediaType.VIDEO;
					media.add(new ScrapedMediaDTO(url, type, position++));
				}
			}
		}
		return List.copyOf(media);
	}

	private static boolean isProfileImage(Element element) {
		Element profileLink = element.closest("a[href^='/@'], a[href*='threads.com/@']");
		return profileLink != null && !profileLink.attr("href").contains("/post/");
	}

	private static Optional<String> headerAvatar(Element root) {
		for (Element image : root.select("header img[src], a[href^='/@'] img[src]")) {
			String url = image.attr("src");
			if (isHttps(url)) {
				return Optional.of(url);
			}
		}
		return Optional.empty();
	}

	private static Optional<String> usernameFromProfileUrl(String rawUrl) {
		if (rawUrl == null) {
			return Optional.empty();
		}
		int marker = rawUrl.indexOf("/@");
		if (marker < 0) {
			return Optional.empty();
		}
		String remainder = rawUrl.substring(marker + 2);
		int slash = remainder.indexOf('/');
		String username = slash >= 0 ? remainder.substring(0, slash) : remainder;
		return username.matches("[A-Za-z0-9._]{1,64}") ? Optional.of(username) : Optional.empty();
	}

	private static boolean isReply(Element root) {
		String text = root.text().toLowerCase(Locale.ROOT);
		return text.contains("replied to") || text.contains("replying to");
	}

	private static boolean isRepost(Element root) {
		String text = root.text().toLowerCase(Locale.ROOT);
		return text.contains("reposted") || text.contains("repost by");
	}

	private static boolean isPostText(String text, String username) {
		String normalized = text.toLowerCase(Locale.ROOT);
		return text.length() > 1 && !UI_TEXT.contains(normalized)
				&& !normalized.equals(username == null ? "" : username.toLowerCase(Locale.ROOT))
				&& !normalized.matches("\\d+[smhdw]")
				&& !normalized.matches("[\\d,.]+\\s*(likes?|replies|views?)");
	}

	private static String resolveLanguage(Element root, Document document, Language fallback) {
		String raw = firstNonBlank(root.attr("lang"), document.selectFirst("[lang]") == null
				? "" : document.selectFirst("[lang]").attr("lang")).toLowerCase(Locale.ROOT);
		for (String supported : List.of("az", "ru", "en", "tr")) {
			if (raw.startsWith(supported)) {
				return supported;
			}
		}
		return (fallback == null ? Language.AZ : fallback).name().toLowerCase(Locale.ROOT);
	}

	private static int rootScore(Element root) {
		int score = root.select(ThreadsSelectors.TIME).isEmpty() ? 0 : 5;
		score += root.select(ThreadsSelectors.PROFILE_LINKS).isEmpty() ? 0 : 3;
		score += root.select(ThreadsSelectors.TEXT).isEmpty() ? 0 : 2;
		score += root.select(ThreadsSelectors.MEDIA).isEmpty() ? 0 : 1;
		return score;
	}

	private static boolean isHttps(String url) {
		return url != null && (url.startsWith("https://") || url.startsWith("//"));
	}

	private static String cleanText(String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
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
