package org.raul.javawebscarper.config;

import org.raul.javawebscarper.browser.BrowserEngineProperties;
import org.raul.javawebscarper.scraper.adapter.xcom.XComProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
		ScrapeSchedulerProperties.class,
		DailyScrapingProperties.class,
		ScraperEngineProperties.class,
		BrowserEngineProperties.class,
		XComProperties.class
})
public class SchedulingConfig {

	@Bean
	public Clock schedulerClock() {
		return Clock.systemDefaultZone();
	}
}
