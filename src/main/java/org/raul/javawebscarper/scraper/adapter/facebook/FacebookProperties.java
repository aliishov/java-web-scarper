package org.raul.javawebscarper.scraper.adapter.facebook;

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
@ConfigurationProperties(prefix = "scraper.facebook")
public class FacebookProperties {

	private boolean enabled = true;
	@Size(max = 256)
	private String baseUrl = "https://www.facebook.com/";
	@Size(max = 1024)
	private String authStatePath = "";
	private boolean authenticationRequired = true;
	@Size(max = 256)
	private String loginUrl = "https://www.facebook.com/login";
	@Size(max = 256)
	private String authVerificationUrl = "https://www.facebook.com/";
	@Positive
	private long loginTimeoutMs = 60_000;
	@Positive
	private long manualVerificationTimeoutMs = 300_000;
	@Size(max = 32)
	private String locale = "en-US";
	@Size(max = 64)
	private String timezoneId = "Asia/Baku";
	private FacebookSearchMode searchMode = FacebookSearchMode.RECENT;
	@Positive
	private int maxScrollAttempts = 80;
	@Positive
	private int noNewPostLimit = 10;
	@PositiveOrZero
	private long scrollDelayMs = 1_400;
	private boolean includeSponsored = false;
	private boolean includeReels = false;
	private boolean includeSharedPosts = true;
	private boolean includeGroupPosts = true;
	private boolean includePagePosts = true;
	private boolean openPostForDetails = true;
	@PositiveOrZero
	private long actionDelayMs = 500;
	@PositiveOrZero
	private long articleOpenDelayMs = 700;
	@Positive
	private long authenticationTimeoutMs = 60_000;
	@Positive
	private long timelineLoadTimeoutMs = 20_000;
}
