package org.raul.javawebscarper.browser;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrowserEngineHealthIndicator implements HealthIndicator {

	private final BrowserEngine browserEngine;

	@Override
	public Health health() {
		return Health.up()
				.withDetail("enabled", browserEngine.isEnabled())
				.withDetail("browserType", browserEngine.getBrowserType())
				.withDetail("initialized", browserEngine.isInitialized())
				.build();
	}
}
