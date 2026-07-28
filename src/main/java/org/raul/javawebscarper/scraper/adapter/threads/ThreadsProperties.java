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
	private String baseUrl = "https://www.threads.com";
	@Size(max = 1024)
	private String authStatePath = "";
	private boolean authenticationRequired = false;
	@Size(max = 32)
	private String locale = "en-US";
	@Size(max = 64)
	private String timezoneId = "Asia/Baku";
	@Positive
	private int maxScrollAttempts = 30;
	@Positive
	private int noNewPostLimit = 4;
	@Positive
	private int maxCandidates = 200;
	@PositiveOrZero
	private long scrollDelayMs = 1_300;
	@PositiveOrZero
	private long actionDelayMs = 500;
	@Positive
	private long pageLoadTimeoutMs = 20_000;
	private boolean includeReplies = false;
	private boolean includeReposts = false;
}
