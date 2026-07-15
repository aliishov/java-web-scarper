package org.raul.javawebscarper.scraper.adapter.xcom;

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
@ConfigurationProperties(prefix = "scraper.x")
public class XComProperties {

	private boolean enabled = true;
	@Size(max = 256)
	private String baseUrl = "https://x.com";
	@Size(max = 1024)
	private String authStatePath = "";
	@Size(max = 256)
	private String loginUrl = "https://x.com/i/flow/login";
	@Positive
	private long loginTimeoutMs = 60_000;
	private boolean authenticationRequired = true;
	private XSearchMode searchMode = XSearchMode.LATEST;
	private boolean includeRetweets = true;
	private boolean includeReplies = true;
	private boolean includePromoted = false;
	@Positive
	private int maxScrollAttempts = 30;
	@PositiveOrZero
	private long scrollDelayMs = 1_200;
	@Positive
	private int noNewPostLimit = 3;
	@Positive
	private long postOpenTimeoutMs = 15_000;
	private boolean preferLatestTab = true;
}
