package org.raul.javawebscarper.service;

import java.util.List;
import java.util.UUID;

public record ScrapedPostIngestionResult(
		int postsReceived,
		int postsCreated,
		int postsUpdated,
		int postsSkipped,
		int mediaCreated,
		int keywordsLinked,
		List<String> errors,
		List<UUID> savedPostIds
) {

	public ScrapedPostIngestionResult {
		if (postsReceived < 0) {
			throw new IllegalArgumentException("postsReceived must not be negative");
		}
		if (postsCreated < 0) {
			throw new IllegalArgumentException("postsCreated must not be negative");
		}
		if (postsUpdated < 0) {
			throw new IllegalArgumentException("postsUpdated must not be negative");
		}
		if (postsSkipped < 0) {
			throw new IllegalArgumentException("postsSkipped must not be negative");
		}
		if (mediaCreated < 0) {
			throw new IllegalArgumentException("mediaCreated must not be negative");
		}
		if (keywordsLinked < 0) {
			throw new IllegalArgumentException("keywordsLinked must not be negative");
		}
		errors = errors == null ? List.of() : List.copyOf(errors);
		savedPostIds = savedPostIds == null ? List.of() : List.copyOf(savedPostIds);
	}

	public int postsSaved() {
		return postsCreated + postsUpdated;
	}

	public boolean allPostsFailed() {
		return postsReceived > 0 && postsSaved() == 0 && postsSkipped == postsReceived && !errors.isEmpty();
	}

	public static ScrapedPostIngestionResult empty() {
		return new ScrapedPostIngestionResult(0, 0, 0, 0, 0, 0, List.of(), List.of());
	}
}
