package org.raul.javawebscarper.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.DateTimeException;
import java.time.ZoneId;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "scraper.daily")
public class DailyScrapingProperties {

	private boolean enabled = true;

	@NotBlank
	private String cron = "0 0 3 * * *";

	@NotBlank
	private String zoneId = "Asia/Baku";

	@Positive
	private int maxJobsPerRun = 100;

	private boolean runOnStartup = false;

	public ZoneId getResolvedZoneId() {
		return ZoneId.of(zoneId);
	}

	@AssertTrue(message = "zoneId must be a valid ZoneId")
	public boolean isZoneIdValid() {
		if (zoneId == null || zoneId.isBlank()) {
			return false;
		}
		try {
			ZoneId.of(zoneId);
			return true;
		} catch (DateTimeException exception) {
			return false;
		}
	}
}
