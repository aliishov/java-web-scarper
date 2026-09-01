package org.raul.javawebscarper.service;

public record ScrapeJobExecutionOutcome(
		boolean success,
		int postsFound,
		int postsSaved
) {

	public static ScrapeJobExecutionOutcome success(int postsFound, int postsSaved) {
		return new ScrapeJobExecutionOutcome(true, postsFound, postsSaved);
	}

	public static ScrapeJobExecutionOutcome failed() {
		return new ScrapeJobExecutionOutcome(false, 0, 0);
	}
}
