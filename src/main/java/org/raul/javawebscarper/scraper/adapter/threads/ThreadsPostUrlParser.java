package org.raul.javawebscarper.scraper.adapter.threads;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ThreadsPostUrlParser {

	private static final Pattern POST_PATH = Pattern.compile("^/@([A-Za-z0-9._]{1,64})/post/([A-Za-z0-9_-]{2,128})/?$");

	private ThreadsPostUrlParser() {
	}

	public static Optional<ThreadsPostUrl> parse(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create(ThreadsScraperSupport.BASE_URL + "/").resolve(rawUrl.trim());
			if (!isThreadsHost(uri.getHost())) {
				return Optional.empty();
			}
			Matcher matcher = POST_PATH.matcher(uri.getPath());
			if (!matcher.matches()) {
				return Optional.empty();
			}
			String username = matcher.group(1);
			String postId = matcher.group(2);
			return Optional.of(new ThreadsPostUrl(
					username,
					postId,
					ThreadsScraperSupport.BASE_URL + "/@" + username + "/post/" + postId
			));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private static boolean isThreadsHost(String host) {
		if (host == null) {
			return false;
		}
		String normalized = host.toLowerCase(Locale.ROOT);
		return normalized.equals("threads.com")
				|| normalized.equals("www.threads.com")
				|| normalized.equals("threads.net")
				|| normalized.equals("www.threads.net");
	}
}
