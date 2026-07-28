package org.raul.javawebscarper.auth;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

@Getter
@Setter
public class SocialAuthAccountProperties {
	private boolean enabled = true;
	private String login = "";
	private String password = "";
	private String authStatePath = "";
	private boolean authenticationRequired = true;
	private String validationUrl = "";
	private String loginUrl = "";

	public Path resolveAuthStatePath(Path stateDir, SocialPlatform platform) {
		if (authStatePath != null && !authStatePath.isBlank()) {
			return Path.of(authStatePath.trim()).toAbsolutePath().normalize();
		}
		return stateDir.resolve(platform.id() + "-storage-state.json").toAbsolutePath().normalize();
	}
}
