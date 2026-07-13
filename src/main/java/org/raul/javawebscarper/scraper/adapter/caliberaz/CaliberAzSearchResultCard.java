package org.raul.javawebscarper.scraper.adapter.caliberaz;

import java.time.OffsetDateTime;

record CaliberAzSearchResultCard(
		String postUrl,
		String title,
		OffsetDateTime searchDate,
		String thumbnailUrl,
		int scrollBatch
) {
}
