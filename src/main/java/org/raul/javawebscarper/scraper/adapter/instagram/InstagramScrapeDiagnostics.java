package org.raul.javawebscarper.scraper.adapter.instagram;

import java.util.LinkedHashMap;
import java.util.Map;

public class InstagramScrapeDiagnostics {

	boolean authStateUsed;
	InstagramAuthenticationStatus authenticationStatus = InstagramAuthenticationStatus.UNKNOWN;
	InstagramSearchMode searchMode;
	String searchQuery;
	boolean keywordSearchSupported;
	boolean hashtagFallbackUsed;
	int searchResultsSeen;
	int postGridLinksSeen;
	int uniquePostCandidates;
	int duplicateCandidatesSkipped;
	int reelsSkipped;
	int sponsoredPostsSkipped;
	int postsOpened;
	int postLoadFailures;
	int captionMoreFound;
	int captionExpanded;
	int captionExpansionFailures;
	int dateParseFailures;
	int tooNewSkipped;
	int tooOldSkipped;
	int mediaOnlyPostsCollected;
	int imagesCollected;
	int videosDetected;
	int carouselsDetected;
	int carouselItemsCollected;
	int scrollAttempts;
	int noNewPostIterations;
	boolean challengeDetected;
	boolean rateLimitDetected;
	boolean authenticationExpiredDuringRun;
	int postsCollected;
	int extractionFailures;

	public Map<String, Object> toMetadata() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("authStateUsed", authStateUsed);
		metadata.put("authenticationStatus", authenticationStatus.name());
		metadata.put("searchMode", searchMode == null ? null : searchMode.name());
		metadata.put("searchQuery", searchQuery);
		metadata.put("keywordSearchSupported", keywordSearchSupported);
		metadata.put("hashtagFallbackUsed", hashtagFallbackUsed);
		metadata.put("searchResultsSeen", searchResultsSeen);
		metadata.put("postGridLinksSeen", postGridLinksSeen);
		metadata.put("uniquePostCandidates", uniquePostCandidates);
		metadata.put("duplicateCandidatesSkipped", duplicateCandidatesSkipped);
		metadata.put("reelsSkipped", reelsSkipped);
		metadata.put("sponsoredPostsSkipped", sponsoredPostsSkipped);
		metadata.put("postsOpened", postsOpened);
		metadata.put("postLoadFailures", postLoadFailures);
		metadata.put("captionMoreFound", captionMoreFound);
		metadata.put("captionExpanded", captionExpanded);
		metadata.put("captionExpansionFailures", captionExpansionFailures);
		metadata.put("dateParseFailures", dateParseFailures);
		metadata.put("tooNewSkipped", tooNewSkipped);
		metadata.put("tooOldSkipped", tooOldSkipped);
		metadata.put("mediaOnlyPostsCollected", mediaOnlyPostsCollected);
		metadata.put("imagesCollected", imagesCollected);
		metadata.put("videosDetected", videosDetected);
		metadata.put("carouselsDetected", carouselsDetected);
		metadata.put("carouselItemsCollected", carouselItemsCollected);
		metadata.put("scrollAttempts", scrollAttempts);
		metadata.put("noNewPostIterations", noNewPostIterations);
		metadata.put("challengeDetected", challengeDetected);
		metadata.put("rateLimitDetected", rateLimitDetected);
		metadata.put("authenticationExpiredDuringRun", authenticationExpiredDuringRun);
		metadata.put("postsCollected", postsCollected);
		metadata.put("extractionFailures", extractionFailures);
		return metadata;
	}
}
