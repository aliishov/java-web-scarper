package org.raul.javawebscarper.scraper.adapter.tiktok;

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
@ConfigurationProperties(prefix = "scraper.tiktok")
public class TikTokProperties {

	private boolean enabled = true;
	@Size(max = 256)
	private String baseUrl = "https://www.tiktok.com";
	@Size(max = 1024)
	private String authStatePath = "";
	private boolean authenticationRequired = false;
	@Size(max = 32)
	private String locale = "en-US";
	@Size(max = 64)
	private String timezoneId = "Asia/Baku";
	private TikTokSearchMode searchMode = TikTokSearchMode.AUTO;
	@Positive
	private long navigationTimeoutMs = 30_000;
	@Positive
	private long readinessTimeoutMs = 20_000;
	@Positive
	private long searchTimeoutMs = 20_000;
	@Positive
	private long postOpenTimeoutMs = 20_000;
	@Positive
	private int maxScrollAttempts = 30;
	@Positive
	private int noNewPostLimit = 4;
	@Positive
	private int maxCandidates = 100;
	@PositiveOrZero
	private long actionDelayMs = 500;
	@PositiveOrZero
	private long scrollDelayMs = 1_300;
	private boolean openPostForDetails = true;
	private boolean includeSponsored = false;
	private boolean includePhotoPosts = true;
}
