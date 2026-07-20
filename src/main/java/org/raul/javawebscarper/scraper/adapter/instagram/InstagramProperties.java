package org.raul.javawebscarper.scraper.adapter.instagram;

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
@ConfigurationProperties(prefix = "scraper.instagram")
public class InstagramProperties {

	private boolean enabled = true;
	@Size(max = 256)
	private String baseUrl = "https://www.instagram.com";
	@Size(max = 1024)
	private String authStatePath = "";
	private boolean authenticationRequired = true;
	@Size(max = 256)
	private String loginUrl = "https://www.instagram.com/accounts/login/";
	@Size(max = 256)
	private String authVerificationUrl = "https://www.instagram.com/";
	@Positive
	private long loginTimeoutMs = 60_000;
	@Positive
	private long manualVerificationTimeoutMs = 300_000;
	@Size(max = 32)
	private String locale = "en-US";
	@Size(max = 64)
	private String timezoneId = "Asia/Baku";
	private InstagramSearchMode searchMode = InstagramSearchMode.AUTO;
	@Positive
	private int maxScrollAttempts = 30;
	@Positive
	private int noNewPostLimit = 4;
	@Positive
	private int maxCandidates = 200;
	@Positive
	private int maxCarouselItems = 20;
	@PositiveOrZero
	private long scrollDelayMs = 1_300;
	@PositiveOrZero
	private long actionDelayMs = 500;
	@Positive
	private long postOpenTimeoutMs = 15_000;
	@Positive
	private long timelineLoadTimeoutMs = 20_000;
	private boolean includeReels = false;
	private boolean includeSponsored = false;
	private boolean openPostForDetails = true;
}
