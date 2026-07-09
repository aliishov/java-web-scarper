package org.raul.javawebscarper.scraper.adapter.oxuaz;

import java.time.OffsetDateTime;

record OxuAzSearchResultCard(
		String postUrl,
		String title,
		OffsetDateTime postDate,
		String thumbnailUrl
) {
}
