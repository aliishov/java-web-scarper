package org.raul.javawebscarper.scraper.adapter.haqqinaz;

import java.time.OffsetDateTime;

record HaqqinAzSearchResultCard(
		String postUrl,
		String title,
		OffsetDateTime searchDate,
		String thumbnailUrl,
		int searchBatch
) {
}
