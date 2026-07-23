package org.raul.javawebscarper.scraper.adapter.threads;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.Locale;

public final class ThreadsPageInspector {

	private ThreadsPageInspector() {
	}

	public static ThreadsPageStatus inspect(String url, String html) {
		String safeUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
		Document document = Jsoup.parse(html == null ? "" : html);
		String text = document.text().toLowerCase(Locale.ROOT);
		if (safeUrl.contains("/challenge") || safeUrl.contains("/checkpoint")
				|| text.contains("challenge required") || text.contains("confirm it's you")) {
			return ThreadsPageStatus.CHALLENGE_REQUIRED;
		}
		if (text.contains("try again later") || text.contains("too many requests")
				|| text.contains("temporarily blocked") || text.contains("rate limit")) {
			return ThreadsPageStatus.RATE_LIMITED;
		}
		if (safeUrl.contains("/login") || document.select("input[name=username], input[name=password]").size() >= 2
				|| text.contains("log in to threads")) {
			return ThreadsPageStatus.LOGIN_REQUIRED;
		}
		if (text.contains("page isn't available") || text.contains("page not found")
				|| text.contains("this content isn't available")) {
			return ThreadsPageStatus.NOT_FOUND;
		}
		if (!document.select(ThreadsSelectors.POST_LINKS + ", " + ThreadsSelectors.POST_ROOT).isEmpty()) {
			return ThreadsPageStatus.READY;
		}
		return ThreadsPageStatus.UNKNOWN;
	}
}
