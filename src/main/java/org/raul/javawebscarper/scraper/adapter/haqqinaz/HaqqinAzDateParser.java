package org.raul.javawebscarper.scraper.adapter.haqqinaz;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class HaqqinAzDateParser {

	public Optional<OffsetDateTime> parseArticleDate(String value) {
		return Optional.empty();
	}
}
