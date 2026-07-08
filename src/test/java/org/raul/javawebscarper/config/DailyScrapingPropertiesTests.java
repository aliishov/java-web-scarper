package org.raul.javawebscarper.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DailyScrapingPropertiesTests {

	@Test
	void bindsDailyScrapingProperties() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("scraper.daily.enabled", "false")
				.withProperty("scraper.daily.cron", "0 15 2 * * *")
				.withProperty("scraper.daily.zone-id", "UTC")
				.withProperty("scraper.daily.max-jobs-per-run", "25")
				.withProperty("scraper.daily.run-on-startup", "true");

		DailyScrapingProperties properties = Binder.get(environment)
				.bind("scraper.daily", Bindable.of(DailyScrapingProperties.class))
				.orElseThrow(() -> new IllegalStateException("Failed to bind daily scraping properties"));

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.getCron()).isEqualTo("0 15 2 * * *");
		assertThat(properties.getZoneId()).isEqualTo("UTC");
		assertThat(properties.getResolvedZoneId()).isEqualTo(ZoneId.of("UTC"));
		assertThat(properties.getMaxJobsPerRun()).isEqualTo(25);
		assertThat(properties.isRunOnStartup()).isTrue();
	}

	@Test
	void defaultsAreUsable() {
		DailyScrapingProperties properties = new DailyScrapingProperties();

		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getCron()).isEqualTo("0 0 3 * * *");
		assertThat(properties.getZoneId()).isEqualTo("Asia/Baku");
		assertThat(properties.getResolvedZoneId()).isEqualTo(ZoneId.of("Asia/Baku"));
		assertThat(properties.getMaxJobsPerRun()).isEqualTo(100);
		assertThat(properties.isRunOnStartup()).isFalse();
		assertThat(properties.isZoneIdValid()).isTrue();
	}
}
