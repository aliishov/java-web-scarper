package org.raul.javawebscarper.scraper.adapter.tiktok;

import java.util.LinkedHashMap;
import java.util.Map;

public class TikTokScrapeDiagnostics {

	public boolean authStateUsed;
	public TikTokAuthenticationStatus authenticationStatus;
	public boolean anonymousSession;
	public boolean anonymousFallbackUsed;
	public boolean authenticationExpiredDuringRun;
	public TikTokAuthenticationStatus authenticationStatusAtFailure;
	public boolean anonymousAccessRevokedDuringRun;
	public TikTokPageReadinessStatus pageReadinessStatus;
	public int readinessAttempts;
	public boolean cookieConsentDetected;
	public boolean loginModalDetected;
	public boolean captchaDetected;
	public boolean verificationDetected;
	public boolean rateLimitDetected;
	public String searchQuery;
	public TikTokSearchMode searchMode;
	public boolean searchNavigationUsed;
	public boolean directSearchFallbackUsed;
	public boolean videosTabFound;
	public boolean videosTabConfirmed;
	public int searchCardsSeen;
	public int candidateLinksSeen;
	public int uniqueCandidates;
	public int duplicateCandidatesSkipped;
	public int postsOpened;
	public int postLoadFailures;
	public int captionPrimaryFound;
	public int captionFallbackUsed;
	public int captionEmpty;
	public int captionMoreFound;
	public int captionExpanded;
	public int captionExpansionFailures;
	public int dateParseFailures;
	public int tooOldSkipped;
	public int tooNewSkipped;
	public int imagesCollected;
	public int videosDetected;
	public int videoUrlsCollected;
	public int videoUrlUnavailable;
	public int photoPostsCollected;
	public int sponsoredPostsSkipped;
	public int scrollAttempts;
	public int noNewPostIterations;
	public int postsCollected;

	public Map<String, Object> toMetadata() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("authStateUsed", authStateUsed);
		if (authenticationStatus != null) {
			metadata.put("authenticationStatus", authenticationStatus.name());
		}
		metadata.put("anonymousSession", anonymousSession);
		metadata.put("anonymousFallbackUsed", anonymousFallbackUsed);
		metadata.put("authenticationExpiredDuringRun", authenticationExpiredDuringRun);
		if (authenticationStatusAtFailure != null) {
			metadata.put("authenticationStatusAtFailure", authenticationStatusAtFailure.name());
		}
		metadata.put("anonymousAccessRevokedDuringRun", anonymousAccessRevokedDuringRun);
		if (pageReadinessStatus != null) {
			metadata.put("pageReadinessStatus", pageReadinessStatus.name());
		}
		metadata.put("readinessAttempts", readinessAttempts);
		metadata.put("cookieConsentDetected", cookieConsentDetected);
		metadata.put("loginModalDetected", loginModalDetected);
		metadata.put("captchaDetected", captchaDetected);
		metadata.put("verificationDetected", verificationDetected);
		metadata.put("rateLimitDetected", rateLimitDetected);
		if (searchQuery != null && !searchQuery.isBlank()) {
			metadata.put("searchQuery", searchQuery);
		}
		if (searchMode != null) {
			metadata.put("searchMode", searchMode.name());
		}
		metadata.put("searchNavigationUsed", searchNavigationUsed);
		metadata.put("directSearchFallbackUsed", directSearchFallbackUsed);
		metadata.put("videosTabFound", videosTabFound);
		metadata.put("videosTabConfirmed", videosTabConfirmed);
		metadata.put("searchCardsSeen", searchCardsSeen);
		metadata.put("candidateLinksSeen", candidateLinksSeen);
		metadata.put("uniqueCandidates", uniqueCandidates);
		metadata.put("duplicateCandidatesSkipped", duplicateCandidatesSkipped);
		metadata.put("postsOpened", postsOpened);
		metadata.put("postLoadFailures", postLoadFailures);
		metadata.put("captionPrimaryFound", captionPrimaryFound);
		metadata.put("captionFallbackUsed", captionFallbackUsed);
		metadata.put("captionEmpty", captionEmpty);
		metadata.put("captionMoreFound", captionMoreFound);
		metadata.put("captionExpanded", captionExpanded);
		metadata.put("captionExpansionFailures", captionExpansionFailures);
		metadata.put("dateParseFailures", dateParseFailures);
		metadata.put("tooOldSkipped", tooOldSkipped);
		metadata.put("tooNewSkipped", tooNewSkipped);
		metadata.put("imagesCollected", imagesCollected);
		metadata.put("videosDetected", videosDetected);
		metadata.put("videoUrlsCollected", videoUrlsCollected);
		metadata.put("videoUrlUnavailable", videoUrlUnavailable);
		metadata.put("photoPostsCollected", photoPostsCollected);
		metadata.put("sponsoredPostsSkipped", sponsoredPostsSkipped);
		metadata.put("scrollAttempts", scrollAttempts);
		metadata.put("noNewPostIterations", noNewPostIterations);
		metadata.put("postsCollected", postsCollected);
		return metadata;
	}
}
