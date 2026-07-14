package org.raul.javawebscarper.scraper.adapter.bakuws;

import java.time.OffsetDateTime;

record BakuWsSearchResultCard(
		String postUrl,
		String title,
		OffsetDateTime postDate,
		String thumbnailUrl,
		int scrollBatch
) {
}
