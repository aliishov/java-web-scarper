package org.raul.javawebscarper.scraper.adapter.mediaaz;

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
public class MediaAzNewsScraperAdapter implements NewsScraperAdapter {

	private final BrowserSessionFactory browserSessionFactory;

	@Override
	public String sourceCode() {
		return MediaAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return MediaAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		log.info(
				"media.az adapter selected but scraping is not implemented yet: source={}, keyword={}",
				context.source().getCode(),
				context.keyword().getWord()
		);
		return ScraperExecutionResult.unsupported("media.az scraping is not implemented yet");
	}
}
