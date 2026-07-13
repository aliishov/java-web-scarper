package org.raul.javawebscarper.scraper.adapter.onenews;

import java.time.OffsetDateTime;

record OneNewsAzSearchResultCard(
		String postUrl,
		String title,
		String snippet,
		OffsetDateTime searchDate,
		String thumbnailUrl,
		int searchPageNumber
) {
}
