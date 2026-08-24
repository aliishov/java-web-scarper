package org.raul.javawebscarper.config;

import org.raul.javawebscarper.browser.BrowserEngineProperties;
import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.scraper.adapter.facebook.FacebookProperties;
import org.raul.javawebscarper.scraper.adapter.instagram.InstagramProperties;
import org.raul.javawebscarper.scraper.adapter.tiktok.TikTokProperties;
import org.raul.javawebscarper.scraper.adapter.xcom.XComProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
		ScrapeSchedulerProperties.class,
		DailyScrapingProperties.class,
		ScraperEngineProperties.class,
		BrowserEngineProperties.class,
		SocialAuthProperties.class,
		FacebookProperties.class,
		InstagramProperties.class,
		TikTokProperties.class,
		XComProperties.class
})
public class SchedulingConfig {

	@Bean
	public Clock schedulerClock() {
		return Clock.systemDefaultZone();
	}

	@Bean("scrapeJobExecutor")
	public Executor scrapeJobExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("scrape-job-");
		executor.initialize();
		return executor;
	}
}
