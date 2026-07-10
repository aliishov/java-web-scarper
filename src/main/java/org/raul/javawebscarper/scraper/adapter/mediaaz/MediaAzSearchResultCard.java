package org.raul.javawebscarper.scraper.adapter.mediaaz;

import java.time.OffsetDateTime;

record MediaAzSearchResultCard(
		String postUrl,
		String title,
		OffsetDateTime postDate,
		String thumbnailUrl
) {
}
