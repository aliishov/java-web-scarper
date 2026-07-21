package org.raul.javawebscarper.scraper.adapter.tiktok;

public record TikTokPostUrl(
		String externalPostId,
		String canonicalUrl,
		String username,
		boolean shortUrl
) {
}
