package org.raul.javawebscarper.scraper.adapter.facebook;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
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
	private final List<String> unparsedDateSamples = new ArrayList<>();
	private final List<String> tooNewDateSamples = new ArrayList<>();
	int tooNewSkipped;
	int tooOldSkipped;
	int emptyPostsSkipped;
	int imagesCollected;
	int videosDetected;
	int scrollAttempts;
	int noNewPostIterations;
	boolean checkpointDetected;
	boolean rateLimitDetected;
	boolean authenticationExpiredDuringRun;
	int postsCollected;

	void recordUnparsedDateCandidates(List<String> candidates) {
		if (unparsedDateSamples.size() >= 5) {
			return;
		}
		candidates.stream()
				.filter(value -> value != null && !value.isBlank())
				.map(value -> value.length() > 160 ? value.substring(0, 160) : value)
				.filter(value -> !unparsedDateSamples.contains(value))
				.limit(5 - unparsedDateSamples.size())
				.forEach(unparsedDateSamples::add);
	}

	void recordTooNewDateSample(String postUrl, java.time.OffsetDateTime postDate, java.time.OffsetDateTime dateTo) {
		if (tooNewDateSamples.size() >= 5) {
			return;
		}
		String sample = "postUrl=%s, postDate=%s, dateTo=%s".formatted(postUrl, postDate, dateTo);
		if (!tooNewDateSamples.contains(sample)) {
			tooNewDateSamples.add(sample);
		}
	}

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
		metadata.put("unparsedDateSamples", List.copyOf(unparsedDateSamples));
		metadata.put("tooNewDateSamples", List.copyOf(tooNewDateSamples));
		metadata.put("tooNewSkipped", tooNewSkipped);
		metadata.put("tooOldSkipped", tooOldSkipped);
		metadata.put("emptyPostsSkipped", emptyPostsSkipped);
		metadata.put("imagesCollected", imagesCollected);
		metadata.put("videosDetected", videosDetected);
		metadata.put("scrollAttempts", scrollAttempts);
		metadata.put("noNewPostIterations", noNewPostIterations);
		metadata.put("checkpointDetected", checkpointDetected);
		metadata.put("rateLimitDetected", rateLimitDetected);
		metadata.put("authenticationExpiredDuringRun", authenticationExpiredDuringRun);
		metadata.put("postsCollected", postsCollected);
		return metadata;
	}
}
