package org.raul.javawebscarper.scraper.adapter.threads;

import java.util.LinkedHashMap;
import java.util.Map;

final class ThreadsScrapeDiagnostics {

	boolean authStateUsed;
	int postLinksSeen;
	int uniqueCandidates;
	int duplicateCandidates;
	int scrollAttempts;
	int noNewIterations;
	int postsOpened;
	int postsCollected;
	int repliesSkipped;
	int repostsSkipped;
	int keywordMismatchSkipped;
	int tooNewSkipped;
	int tooOldSkipped;
	int dateParseFailures;
	int extractionFailures;
	int postLoadFailures;
	ThreadsPageStatus pageStatus = ThreadsPageStatus.UNKNOWN;

	Map<String, Object> toMetadata() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("authStateUsed", authStateUsed);
		values.put("pageStatus", pageStatus.name());
		values.put("postLinksSeen", postLinksSeen);
		values.put("uniqueCandidates", uniqueCandidates);
		values.put("duplicateCandidates", duplicateCandidates);
		values.put("scrollAttempts", scrollAttempts);
		values.put("noNewIterations", noNewIterations);
		values.put("postsOpened", postsOpened);
		values.put("postsCollected", postsCollected);
		values.put("repliesSkipped", repliesSkipped);
		values.put("repostsSkipped", repostsSkipped);
		values.put("keywordMismatchSkipped", keywordMismatchSkipped);
		values.put("tooNewSkipped", tooNewSkipped);
		values.put("tooOldSkipped", tooOldSkipped);
		values.put("dateParseFailures", dateParseFailures);
		values.put("extractionFailures", extractionFailures);
		values.put("postLoadFailures", postLoadFailures);
		return Map.copyOf(values);
	}
}
