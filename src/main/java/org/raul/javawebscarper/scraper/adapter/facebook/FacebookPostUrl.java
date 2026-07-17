package org.raul.javawebscarper.scraper.adapter.facebook;

public record FacebookPostUrl(
		String externalPostId,
		String canonicalUrl,
		String authorExternalId,
		FacebookPostType type
) {

	public boolean isReel() {
		return type == FacebookPostType.REEL;
	}
}
