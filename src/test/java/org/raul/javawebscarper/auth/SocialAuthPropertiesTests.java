package org.raul.javawebscarper.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SocialAuthPropertiesTests {
	@Test
	void buildsDefaultStatePathFromStateDirectory() {
		SocialAuthProperties properties = new SocialAuthProperties();
		properties.setStateDir(Path.of("./local/auth-test"));

		assertThat(properties.statePath(SocialPlatform.THREADS).toString().replace('\\', '/'))
				.endsWith("local/auth-test/threads-storage-state.json");
	}

	@Test
	void allowsFiveMinutesForInteractiveLoginByDefault() {
		assertThat(new SocialAuthProperties().getInteractiveLoginTimeoutMs()).isEqualTo(300_000);
	}
}
