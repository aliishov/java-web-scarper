package org.raul.javawebscarper.scraper.adapter.instagram;

public record InstagramPostCandidate(
		String externalPostId,
		String postUrl,
		InstagramPostType postType,
		String thumbnailUrl
) {
}
