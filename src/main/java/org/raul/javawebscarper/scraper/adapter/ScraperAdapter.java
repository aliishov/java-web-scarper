package org.raul.javawebscarper.scraper.adapter;

import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;

public interface ScraperAdapter {

	String sourceCode();

	boolean supports(Source source);

	ScraperExecutionResult scrape(ScraperExecutionContext context);
}
