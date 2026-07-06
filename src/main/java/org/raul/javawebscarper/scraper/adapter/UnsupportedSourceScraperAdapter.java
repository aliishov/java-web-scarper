package org.raul.javawebscarper.scraper.adapter;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class UnsupportedSourceScraperAdapter implements ScraperAdapter {

	public static final String SOURCE_CODE = "__unsupported__";

	@Override
	public String sourceCode() {
		return SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return false;
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		String sourceCode = context.source() == null ? "unknown" : context.source().getCode();
		return ScraperExecutionResult.unsupported(
				"No scraper adapter is registered for source '%s'".formatted(sourceCode)
		);
	}
}
