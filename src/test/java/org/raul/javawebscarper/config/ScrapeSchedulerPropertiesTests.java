package org.raul.javawebscarper.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapeSchedulerPropertiesTests {

	@Test
	void bindsSchedulerProperties() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("scraper.scheduler.enabled", "false")
				.withProperty("scraper.scheduler.cron", "0 0 * * * *")
				.withProperty("scraper.scheduler.lookback-amount", "7")
				.withProperty("scraper.scheduler.lookback-unit", "DAYS")
				.withProperty("scraper.scheduler.max-jobs-per-run", "25")
				.withProperty("scraper.scheduler.run-on-startup", "true");

		ScrapeSchedulerProperties properties = Binder.get(environment)
				.bind("scraper.scheduler", Bindable.of(ScrapeSchedulerProperties.class))
				.orElseThrow(() -> new IllegalStateException("Failed to bind scrape scheduler properties"));

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.getCron()).isEqualTo("0 0 * * * *");
		assertThat(properties.getLookbackAmount()).isEqualTo(7);
		assertThat(properties.getLookbackUnit()).isEqualTo(ChronoUnit.DAYS);
		assertThat(properties.getMaxJobsPerRun()).isEqualTo(25);
		assertThat(properties.isRunOnStartup()).isTrue();
	}

	@Test
	void defaultsAreUsable() {
		ScrapeSchedulerProperties properties = new ScrapeSchedulerProperties();

		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getCron()).isEqualTo("0 */30 * * * *");
		assertThat(properties.getLookbackAmount()).isEqualTo(1);
		assertThat(properties.getLookbackUnit()).isEqualTo(ChronoUnit.DAYS);
		assertThat(properties.getMaxJobsPerRun()).isEqualTo(100);
		assertThat(properties.isRunOnStartup()).isFalse();
		assertThat(properties.isLookbackUnitSupported()).isTrue();
	}
}
