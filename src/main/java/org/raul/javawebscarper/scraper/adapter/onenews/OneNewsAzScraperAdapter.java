package org.raul.javawebscarper.scraper.adapter.onenews;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.browser.BrowserSessionFactory;
import org.raul.javawebscarper.dto.scraper.ScrapedAuthorDTO;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.NewsScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OneNewsAzScraperAdapter implements NewsScraperAdapter {

	private static final ScrapedAuthorDTO AUTHOR = new ScrapedAuthorDTO(
			"1news.az",
			"1news.az",
			"1news.az",
			OneNewsAzScraperSupport.BASE_URL,
			null
	);

	private final BrowserSessionFactory browserSessionFactory;
	private final OneNewsAzDateParser dateParser;

	@Override
	public String sourceCode() {
		return OneNewsAzScraperSupport.SOURCE_CODE;
	}

	@Override
	public boolean supports(Source source) {
		return OneNewsAzScraperSupport.supports(source);
	}

	@Override
	public ScraperExecutionResult scrape(ScraperExecutionContext context) {
		log.info(
				"Starting 1news.az scraping skeleton: source={}, keyword={}",
				context.source().getCode(),
				context.keyword().getWord()
		);
		return ScraperExecutionResult.empty();
	}
}
