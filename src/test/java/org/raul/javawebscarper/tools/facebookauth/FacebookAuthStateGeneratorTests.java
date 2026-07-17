package org.raul.javawebscarper.tools.facebookauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacebookAuthStateGeneratorTests {

	@TempDir
	private Path tempDir;

	@Test
	void buildsRequestFromEnvironment() {
		FacebookAuthStateGenerator generator = new FacebookAuthStateGenerator(
				Map.of(
						"FACEBOOK_AUTH_STATE_PATH", "playwright/.auth/facebook-storage-state.json",
						"FACEBOOK_MANUAL_VERIFICATION_TIMEOUT_MS", "90000",
						"FACEBOOK_LOGIN_URL", "https://www.facebook.com/login",
						"FACEBOOK_AUTH_VERIFICATION_URL", "https://www.facebook.com/",
						"FACEBOOK_LOCALE", "az-AZ",
						"FACEBOOK_TIMEZONE_ID", "Asia/Baku"
				),
				output()
		);

		FacebookAuthStateGenerator.FacebookAuthStateRequest request = generator.buildRequest();

		assertThat(request.authStatePath()).isAbsolute();
		assertThat(request.authStatePath().toString().replace('\\', '/')).endsWith("playwright/.auth/facebook-storage-state.json");
		assertThat(request.manualVerificationTimeoutMs()).isEqualTo(90_000);
		assertThat(request.loginUrl()).isEqualTo("https://www.facebook.com/login");
		assertThat(request.authVerificationUrl()).isEqualTo("https://www.facebook.com/");
		assertThat(request.locale()).isEqualTo("az-AZ");
		assertThat(request.timezoneId()).isEqualTo("Asia/Baku");
	}

	@Test
	void rejectsMissingAuthStatePath() {
		FacebookAuthStateGenerator generator = new FacebookAuthStateGenerator(Map.of(), output());

		assertThatThrownBy(generator::buildRequest)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("FACEBOOK_AUTH_STATE_PATH");
	}

	@Test
	void createsParentDirectoryForAuthStatePath() {
		FacebookAuthStateGenerator generator = new FacebookAuthStateGenerator(Map.of(), output());
		Path authStatePath = tempDir.resolve("nested/auth/facebook-storage-state.json");

		generator.validateAuthStatePath(authStatePath);

		assertThat(Files.isDirectory(authStatePath.getParent())).isTrue();
	}

	@Test
	void rejectsDirectoryAsAuthStatePath() {
		FacebookAuthStateGenerator generator = new FacebookAuthStateGenerator(Map.of(), output());

		assertThatThrownBy(() -> generator.validateAuthStatePath(tempDir))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must point to a file");
	}

	@Test
	void rejectsInvalidTimeoutValue() {
		FacebookAuthStateGenerator generator = new FacebookAuthStateGenerator(
				Map.of(
						"FACEBOOK_AUTH_STATE_PATH", tempDir.resolve("facebook-storage-state.json").toString(),
						"FACEBOOK_MANUAL_VERIFICATION_TIMEOUT_MS", "not-a-number"
				),
				output()
		);

		assertThatThrownBy(generator::buildRequest)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("FACEBOOK_MANUAL_VERIFICATION_TIMEOUT_MS");
	}

	private PrintStream output() {
		return new PrintStream(new ByteArrayOutputStream());
	}
}
