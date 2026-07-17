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
	private FacebookSearchMode searchMode = FacebookSearchMode.RECENT;
	@Positive
	private int maxScrollAttempts = 30;
	@Positive
	private int noNewPostLimit = 4;
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
