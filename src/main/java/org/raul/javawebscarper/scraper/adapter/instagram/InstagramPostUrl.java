package org.raul.javawebscarper.scraper.adapter.instagram;

public record InstagramPostUrl(
		String externalPostId,
		String canonicalUrl,
		InstagramPostType type
) {

	public boolean isReel() {
		return type == InstagramPostType.REEL;
	}
}
