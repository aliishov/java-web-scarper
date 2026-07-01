package org.raul.javawebscarper.scraper;

import org.raul.javawebscarper.model.ScrapeJob;

public interface ScraperRunner {

	ScraperResult run(ScrapeJob job);
}
