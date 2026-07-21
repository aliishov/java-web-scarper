package org.raul.javawebscarper.scraper.adapter.tiktok;

import java.time.OffsetDateTime;

public record TikTokPostCandidate(
		String externalPostId,
		String postUrl,
		String username,
		String thumbnailUrl,
		OffsetDateTime cardDate,
		String captionPreview
) {
}
