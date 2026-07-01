package org.raul.javawebscarper.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "scraper.scheduler")
public class ScrapeSchedulerProperties {

	private static final Set<ChronoUnit> SUPPORTED_LOOKBACK_UNITS = EnumSet.of(
			ChronoUnit.HOURS,
			ChronoUnit.DAYS,
			ChronoUnit.WEEKS,
			ChronoUnit.MONTHS
	);

	private boolean enabled = true;

	@NotBlank
	private String cron = "0 */30 * * * *";

	@Positive
	private long lookbackAmount = 1;

	@NotNull
	private ChronoUnit lookbackUnit = ChronoUnit.DAYS;

	@Positive
	private int maxJobsPerRun = 100;

	private boolean runOnStartup = false;

	@AssertTrue(message = "lookbackUnit must be one of HOURS, DAYS, WEEKS, MONTHS")
	public boolean isLookbackUnitSupported() {
		return lookbackUnit != null && SUPPORTED_LOOKBACK_UNITS.contains(lookbackUnit);
	}
}
