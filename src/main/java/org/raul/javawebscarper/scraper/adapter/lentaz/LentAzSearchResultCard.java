package org.raul.javawebscarper.scraper.adapter.lentaz;

import java.time.OffsetDateTime;

record LentAzSearchResultCard(
		String postUrl,
		String externalPostId,
		String title,
		OffsetDateTime searchDate,
		String thumbnailUrl,
		int searchPageNumber
) {
}
