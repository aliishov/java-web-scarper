package org.raul.javawebscarper.config;

import org.raul.javawebscarper.browser.BrowserEngineProperties;
import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.scraper.adapter.facebook.FacebookProperties;
import org.raul.javawebscarper.scraper.adapter.instagram.InstagramProperties;
import org.raul.javawebscarper.scraper.adapter.threads.ThreadsProperties;
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
		ScrapeJobExecutionProperties.class,
		BrowserEngineProperties.class,
		SocialAuthProperties.class,
		FacebookProperties.class,
		InstagramProperties.class,
		ThreadsProperties.class,
		TikTokProperties.class,
		XComProperties.class
})
public class SchedulingConfig {

	@Bean
	public Clock schedulerClock() {
		return Clock.systemDefaultZone();
	}

	@Bean("scrapeJobExecutor")
	public Executor scrapeJobExecutor(ScrapeJobExecutionProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.getMaxConcurrency());
		executor.setMaxPoolSize(properties.getMaxConcurrency());
		executor.setQueueCapacity(properties.getMaxConcurrency() * 25);
		executor.setThreadNamePrefix("scrape-job-");
		executor.initialize();
		return executor;
	}
}
