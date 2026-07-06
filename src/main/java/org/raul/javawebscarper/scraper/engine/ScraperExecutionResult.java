package org.raul.javawebscarper.scraper.engine;

import org.raul.javawebscarper.dto.scraper.ScrapedPostDTO;

import java.time.OffsetDateTime;
import java.util.List;

public record ScraperExecutionResult(
		ScraperExecutionStatus status,
		List<ScrapedPostDTO> posts,
		int postsFound,
		int postsSkipped,
		String errorMessage,
		OffsetDateTime startedAt,
		OffsetDateTime finishedAt
) {

	public ScraperExecutionResult {
		posts = posts == null ? List.of() : List.copyOf(posts);
		if (postsFound < 0) {
			throw new IllegalArgumentException("postsFound must not be negative");
		}
		if (postsSkipped < 0) {
			throw new IllegalArgumentException("postsSkipped must not be negative");
		}
		postsFound = Math.max(postsFound, posts.size());
	}

	public static ScraperExecutionResult success(List<ScrapedPostDTO> posts) {
		List<ScrapedPostDTO> safePosts = posts == null ? List.of() : List.copyOf(posts);
		return new ScraperExecutionResult(
				ScraperExecutionStatus.SUCCESS,
				safePosts,
				safePosts.size(),
				0,
				null,
				null,
				null
		);
	}

	public static ScraperExecutionResult empty() {
		return new ScraperExecutionResult(ScraperExecutionStatus.EMPTY, List.of(), 0, 0, null, null, null);
	}

	public static ScraperExecutionResult failed(String message) {
		return new ScraperExecutionResult(ScraperExecutionStatus.FAILED, List.of(), 0, 0, message, null, null);
	}

	public static ScraperExecutionResult unsupported(String message) {
		return new ScraperExecutionResult(ScraperExecutionStatus.UNSUPPORTED, List.of(), 0, 0, message, null, null);
	}

	public ScraperExecutionResult withTiming(OffsetDateTime startedAt, OffsetDateTime finishedAt) {
		return new ScraperExecutionResult(status, posts, postsFound, postsSkipped, errorMessage, startedAt, finishedAt);
	}
}
