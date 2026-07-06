package org.raul.javawebscarper.dto.scraper;

public record ScrapedAuthorDTO(
		String externalId,
		String username,
		String displayName,
		String profileUrl,
		String avatarUrl
) {
}
