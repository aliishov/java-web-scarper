package org.raul.javawebscarper.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "scraper.auth")
public class SocialAuthProperties {
	private boolean bootstrapEnabled = true;
	private boolean failStartupIfAuthFails = false;
	private Path stateDir = Path.of("./local/auth");
	private SocialAuthAccountProperties x = account("https://x.com/i/flow/login", "https://x.com/home");
	private SocialAuthAccountProperties tiktok = account("https://www.tiktok.com/login", "https://www.tiktok.com/");
	private SocialAuthAccountProperties instagram = account("https://www.instagram.com/accounts/login/", "https://www.instagram.com/");
	private SocialAuthAccountProperties facebook = account("https://www.facebook.com/login", "https://www.facebook.com/");
	private SocialAuthAccountProperties threads = account("https://www.threads.net/login", "https://www.threads.net/");

	public SocialAuthAccountProperties account(SocialPlatform platform) {
		return switch (platform) {
			case X -> x;
			case TIKTOK -> tiktok;
			case INSTAGRAM -> instagram;
			case FACEBOOK -> facebook;
			case THREADS -> threads;
		};
	}

	public Path statePath(SocialPlatform platform) {
		return account(platform).resolveAuthStatePath(stateDir, platform);
	}

	public Map<SocialPlatform, SocialAuthAccountProperties> accounts() {
		Map<SocialPlatform, SocialAuthAccountProperties> accounts = new EnumMap<>(SocialPlatform.class);
		for (SocialPlatform platform : SocialPlatform.values()) {
			accounts.put(platform, account(platform));
		}
		return accounts;
	}

	private static SocialAuthAccountProperties account(String loginUrl, String validationUrl) {
		SocialAuthAccountProperties value = new SocialAuthAccountProperties();
		value.setLoginUrl(loginUrl);
		value.setValidationUrl(validationUrl);
		return value;
	}
}
