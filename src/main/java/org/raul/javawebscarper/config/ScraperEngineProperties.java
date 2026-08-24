package org.raul.javawebscarper.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "scraper.engine")
public class ScraperEngineProperties {

	@Positive
	private int maxPages = 10;

	@Positive
	private int maxPosts = 300;

	private boolean failOnUnsupportedSource = true;

	private boolean normalizeUrls = true;

	private boolean removeTrackingParams = true;
}
