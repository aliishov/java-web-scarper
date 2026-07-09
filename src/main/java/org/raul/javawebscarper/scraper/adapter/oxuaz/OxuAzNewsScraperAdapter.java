package org.raul.javawebscarper.scraper.adapter.oxuaz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.NewsScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OxuAzNewsScraperAdapter implements NewsScraperAdapter {

	@Override
	public String sourceCode() {
		return OxuAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return OxuAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		log.info(
				"Oxu.az scraper adapter is registered: keyword={}, dateFrom={}, dateTo={}",
				context.keyword().getWord(),
				context.dateFrom(),
				context.dateTo()
		);
		return ScraperExecutionResult.empty();
	}
}
