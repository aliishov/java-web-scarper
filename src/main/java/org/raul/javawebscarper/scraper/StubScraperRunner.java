package org.raul.javawebscarper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.model.ScrapeJob;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StubScraperRunner implements ScraperRunner {

	@Override
	public ScraperResult run(ScrapeJob job) {
		log.info(
				"Stub scraper executed for jobId={}, source={}, keyword={}, dateFrom={}, dateTo={}",
				job.getId(),
				job.getSource().getCode(),
				job.getKeyword().getWord(),
				job.getDateFrom(),
				job.getDateTo()
		);
		return ScraperResult.empty();
	}
}
