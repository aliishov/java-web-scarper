package org.raul.javawebscarper.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "scraper.execution")
public class ScrapeJobExecutionProperties {

	private boolean enabled = true;
	@Positive
	private int maxConcurrency = 4;
	@Positive
	private int perSourceConcurrency = 1;
	@NotNull
	private Duration jobTimeout = Duration.ofMinutes(30);
}
