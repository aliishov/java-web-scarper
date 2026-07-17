package org.raul.javawebscarper.scraper.adapter.facebook;

import java.util.LinkedHashMap;
import java.util.Map;

public class FacebookScrapeDiagnostics {

	boolean authStateUsed;
	FacebookAuthenticationStatus authenticationStatus = FacebookAuthenticationStatus.UNKNOWN;
	String searchQuery;
	boolean postsFilterConfirmed;
	boolean recentFilterConfirmed;
	int containersSeen;
	int validPostCandidates;
	int duplicatePostsSkipped;
	int sponsoredPostsSkipped;
	int reelsSkipped;
	int sharedPostsCollected;
	int groupPostsCollected;
	int seeMoreButtonsFound;
	int seeMoreExpanded;
	int seeMoreFailures;
	int dateParseFailures;
	int tooNewSkipped;
	int tooOldSkipped;
	int emptyPostsSkipped;
	int imagesCollected;
	int videosDetected;
	int scrollAttempts;
	int noNewPostIterations;
	boolean checkpointDetected;
	boolean rateLimitDetected;
	int postsCollected;

	Map<String, Object> toMetadata() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("authStateUsed", authStateUsed);
		metadata.put("authenticationStatus", authenticationStatus.name());
		metadata.put("searchQuery", searchQuery);
		metadata.put("postsFilterConfirmed", postsFilterConfirmed);
		metadata.put("recentFilterConfirmed", recentFilterConfirmed);
		metadata.put("containersSeen", containersSeen);
		metadata.put("validPostCandidates", validPostCandidates);
		metadata.put("duplicatePostsSkipped", duplicatePostsSkipped);
		metadata.put("sponsoredPostsSkipped", sponsoredPostsSkipped);
		metadata.put("reelsSkipped", reelsSkipped);
		metadata.put("sharedPostsCollected", sharedPostsCollected);
		metadata.put("groupPostsCollected", groupPostsCollected);
		metadata.put("seeMoreButtonsFound", seeMoreButtonsFound);
		metadata.put("seeMoreExpanded", seeMoreExpanded);
		metadata.put("seeMoreFailures", seeMoreFailures);
		metadata.put("dateParseFailures", dateParseFailures);
		metadata.put("tooNewSkipped", tooNewSkipped);
		metadata.put("tooOldSkipped", tooOldSkipped);
		metadata.put("emptyPostsSkipped", emptyPostsSkipped);
		metadata.put("imagesCollected", imagesCollected);
		metadata.put("videosDetected", videosDetected);
		metadata.put("scrollAttempts", scrollAttempts);
		metadata.put("noNewPostIterations", noNewPostIterations);
		metadata.put("checkpointDetected", checkpointDetected);
		metadata.put("rateLimitDetected", rateLimitDetected);
		metadata.put("postsCollected", postsCollected);
		return metadata;
	}
}
