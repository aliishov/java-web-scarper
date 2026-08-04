package org.raul.javawebscarper.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
		@NotBlank String secret,
		@NotBlank String issuer,
		@NotNull Duration accessTokenTtl,
		@NotNull Duration refreshTokenTtl
) {
	public JwtProperties {
		if (secret == null || secret.length() < 32) {
			throw new IllegalArgumentException("security.jwt.secret must contain at least 32 characters");
		}
	}
}
