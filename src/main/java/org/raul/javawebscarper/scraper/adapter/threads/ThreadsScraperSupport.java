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

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
	private static final Set<String> SUPPORTED_CODES = Set.of(
			"THREADS", "THREADS_COM", "THREADS_NET", "META_THREADS"
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

	public static List<ThreadsPostCandidate> discoverCandidates(Document document, int maxCandidates) {
		Map<String, ThreadsPostCandidate> candidates = new LinkedHashMap<>();
		for (Element link : document.select(ThreadsSelectors.POST_LINKS)) {
			String href = firstNonBlank(link.absUrl("href"), link.attr("href"));
			ThreadsPostUrlParser.parse(href).ifPresent(postUrl -> candidates.putIfAbsent(
					postUrl.externalPostId(),
					new ThreadsPostCandidate(postUrl.username(), postUrl.externalPostId(), postUrl.canonicalUrl())
			));
			if (candidates.size() >= maxCandidates) {
				break;
			}
		}
		return List.copyOf(candidates.values());
	}

	public static Optional<ScrapedPostDTO> extractPost(
			Document document,
			ScraperExecutionContext context,
			ThreadsPostCandidate candidate,
			ThreadsDateParser dateParser
	) {
		Element root = document.selectFirst(ThreadsSelectors.POST_ROOT);
		if (root == null) {
			return Optional.empty();
		}
		ScrapedAuthorDTO author = extractAuthor(root, candidate.username());
		OffsetDateTime postDate = extractDate(root, dateParser).orElse(null);
		String text = extractText(root, author.username());
		List<ScrapedMediaDTO> media = extractMedia(root);
		if (postDate == null || (text.isBlank() && media.isEmpty())) {
			return Optional.empty();
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source", SOURCE_CODE);
		metadata.put("keyword", context.keyword().getWord());
		metadata.put("postType", "THREAD");
		return Optional.of(new ScrapedPostDTO(
				candidate.externalPostId(),
				candidate.postUrl(),
				postDate,
				author,
				text,
				resolveLanguage(document, context.keyword().getLanguage()),
				media,
				metadata
		));
	}

	private static ScrapedAuthorDTO extractAuthor(Element root, String fallbackUsername) {
		for (Element link : root.select(ThreadsSelectors.PROFILE_LINKS)) {
			String href = firstNonBlank(link.absUrl("href"), link.attr("href"));
			String username = usernameFromUrl(href).orElse(null);
			if (username != null) {
				String displayName = link.text().isBlank() ? username : link.text().trim();
				return new ScrapedAuthorDTO(
						username.toLowerCase(Locale.ROOT),
						username,
						displayName,
						BASE_URL + "/@" + username,
						avatarUrl(root)
				);
			}
		}
		return new ScrapedAuthorDTO(
				fallbackUsername.toLowerCase(Locale.ROOT),
				fallbackUsername,
				fallbackUsername,
				BASE_URL + "/@" + fallbackUsername,
				avatarUrl(root)
		);
	}

	private static Optional<OffsetDateTime> extractDate(Element root, ThreadsDateParser parser) {
		for (Element time : root.select(ThreadsSelectors.TIME)) {
			Optional<OffsetDateTime> parsed = parser.parse(firstNonBlank(time.attr("datetime"), time.text()));
			if (parsed.isPresent()) {
				return parsed;
			}
		}
		return Optional.empty();
	}

	private static String extractText(Element root, String username) {
		for (Element element : root.select(ThreadsSelectors.POST_TEXT)) {
			String text = element.text().trim();
			if (!text.isBlank() && !text.equalsIgnoreCase(username) && element.select("time").isEmpty()) {
				return text;
			}
		}
		return "";
	}

	private static List<ScrapedMediaDTO> extractMedia(Element root) {
		List<ScrapedMediaDTO> media = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		int position = 0;
		for (Element element : root.select(ThreadsSelectors.MEDIA)) {
			if (element.normalName().equals("img")) {
				String url = firstNonBlank(element.attr("src"), element.attr("data-src"));
				String alt = element.attr("alt").toLowerCase(Locale.ROOT);
				if (isHttpUrl(url) && !alt.contains("profile picture") && seen.add(url)) {
					media.add(new ScrapedMediaDTO(url, MediaType.IMAGE, position++));
				}
			} else {
				String videoUrl = element.attr("src");
				if (isHttpUrl(videoUrl) && seen.add(videoUrl)) {
					media.add(new ScrapedMediaDTO(videoUrl, MediaType.VIDEO, position++));
				}
				String posterUrl = element.attr("poster");
				if (isHttpUrl(posterUrl) && seen.add(posterUrl)) {
					media.add(new ScrapedMediaDTO(posterUrl, MediaType.IMAGE, position++));
				}
			}
		}
		return List.copyOf(media);
	}

	private static String avatarUrl(Element root) {
		for (Element image : root.select("img[src]")) {
			if (image.attr("alt").toLowerCase(Locale.ROOT).contains("profile picture")) {
				return image.attr("src");
			}
		}
		return null;
	}

	private static Optional<String> usernameFromUrl(String url) {
		if (url == null) {
			return Optional.empty();
		}
		int marker = url.indexOf("/@");
		if (marker < 0) {
			return Optional.empty();
		}
		String username = url.substring(marker + 2).split("[/?#]", 2)[0];
		return username.isBlank() ? Optional.empty() : Optional.of(username);
	}

	private static String resolveLanguage(Document document, Language fallback) {
		String language = document.select("html").attr("lang");
		return language.isBlank() ? (fallback == null ? Language.AZ.name() : fallback.name()) : language;
	}

	private static boolean isHttpUrl(String value) {
		return value != null && (value.startsWith("https://") || value.startsWith("http://"));
	}

	private static String firstNonBlank(String first, String second) {
		return first != null && !first.isBlank() ? first : second == null ? "" : second;
	}
}
