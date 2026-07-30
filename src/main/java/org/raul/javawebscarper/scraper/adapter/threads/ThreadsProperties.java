package org.raul.javawebscarper.scraper.adapter.threads;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "scraper.threads")
public class ThreadsProperties {

	private boolean enabled = true;
	@Size(max = 256)
	private String baseUrl = ThreadsScraperSupport.BASE_URL;
	@Size(max = 1024)
	private String authStatePath = "";
	private boolean authenticationRequired = true;
	@Size(max = 32)
	private String locale = "en-US";
	@Size(max = 64)
	private String timezoneId = "Asia/Baku";
	@Positive
	private long loginTimeoutMs = 60_000;
	@Positive
	private long timelineLoadTimeoutMs = 20_000;
	@Positive
	private long postOpenTimeoutMs = 15_000;
	@PositiveOrZero
	private long actionDelayMs = 500;
	@PositiveOrZero
	private long scrollDelayMs = 1_200;
	@Positive
	private int maxScrollAttempts = 30;
	@Positive
	private int noNewPostLimit = 4;
	@Positive
	private int maxCandidates = 200;
}
