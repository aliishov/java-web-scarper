package org.raul.javawebscarper.scraper.adapter.haqqinaz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.browser.BrowserSessionFactory;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.NewsScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HaqqinAzNewsScraperAdapter implements NewsScraperAdapter {

	private final BrowserSessionFactory browserSessionFactory;
	private final HaqqinAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return HaqqinAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return HaqqinAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		log.info(
				"haqqin.az scraper adapter is registered but implementation is not completed yet: source={}, keyword={}",
				context.source().getCode(),
				context.keyword().getWord()
		);
		return ScraperExecutionResult.empty();
	}
}
