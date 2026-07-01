package org.raul.javawebscarper.scraper;

import java.util.List;
import java.util.UUID;

public record ScraperResult(
		int postsFound,
		int postsSaved,
		List<UUID> savedPostIds
) {

	public ScraperResult {
		if (postsFound < 0) {
			throw new IllegalArgumentException("postsFound must not be negative");
		}
		if (postsSaved < 0) {
			throw new IllegalArgumentException("postsSaved must not be negative");
		}
		if (postsSaved > postsFound) {
			throw new IllegalArgumentException("postsSaved must not be greater than postsFound");
		}
		savedPostIds = savedPostIds == null ? List.of() : List.copyOf(savedPostIds);
	}

	public static ScraperResult empty() {
		return new ScraperResult(0, 0, List.of());
	}
}
