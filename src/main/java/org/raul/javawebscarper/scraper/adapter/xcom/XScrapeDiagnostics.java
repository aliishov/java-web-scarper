package org.raul.javawebscarper.scraper.adapter.xcom;

import java.util.LinkedHashMap;
import java.util.Map;

public class XScrapeDiagnostics {

	int timelinePostsSeen;
	int uniqueStatusIds;
	int promotedPostsSkipped;
	int duplicatesSkipped;
	int tooNewSkipped;
	int tooOldSkipped;
	int dateParseFailures;
	int emptyPostsSkipped;
	int mediaOnlyPostsCollected;
	int replyPostsCollected;
	int repostsCollected;
	int imagesCollected;
	int videosDetected;
	int scrollAttempts;
	int noNewPostIterations;
	int loginWallDetected;
	int rateLimitDetected;
	int postsCollected;
	boolean latestModeConfirmed;
	boolean authStateUsed;

	Map<String, Object> toMetadata() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("timelinePostsSeen", timelinePostsSeen);
		metadata.put("uniqueStatusIds", uniqueStatusIds);
		metadata.put("promotedPostsSkipped", promotedPostsSkipped);
		metadata.put("duplicatesSkipped", duplicatesSkipped);
		metadata.put("tooNewSkipped", tooNewSkipped);
		metadata.put("tooOldSkipped", tooOldSkipped);
		metadata.put("dateParseFailures", dateParseFailures);
		metadata.put("emptyPostsSkipped", emptyPostsSkipped);
		metadata.put("mediaOnlyPostsCollected", mediaOnlyPostsCollected);
		metadata.put("replyPostsCollected", replyPostsCollected);
		metadata.put("repostsCollected", repostsCollected);
		metadata.put("imagesCollected", imagesCollected);
		metadata.put("videosDetected", videosDetected);
		metadata.put("scrollAttempts", scrollAttempts);
		metadata.put("noNewPostIterations", noNewPostIterations);
		metadata.put("loginWallDetected", loginWallDetected);
		metadata.put("rateLimitDetected", rateLimitDetected);
		metadata.put("postsCollected", postsCollected);
		metadata.put("latestModeConfirmed", latestModeConfirmed);
		metadata.put("authStateUsed", authStateUsed);
		return metadata;
	}
}
