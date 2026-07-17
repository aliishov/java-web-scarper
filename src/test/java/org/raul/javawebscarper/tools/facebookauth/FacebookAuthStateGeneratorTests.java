package org.raul.javawebscarper.tools.facebookauth;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacebookAuthStateGeneratorTests {

	@Test
	void buildsRequestFromEnvironment() {
		FacebookAuthStateGenerator generator = new FacebookAuthStateGenerator(
				Map.of(
						"FACEBOOK_AUTH_STATE_PATH", "playwright/.auth/facebook-storage-state.json",
						"FACEBOOK_AUTH_TIMEOUT_MS", "90000"
				),
				output()
		);

		FacebookAuthStateGenerator.FacebookAuthStateRequest request = generator.buildRequest();

		assertThat(request.authStatePath()).isEqualTo(Path.of("playwright/.auth/facebook-storage-state.json"));
		assertThat(request.timeoutMs()).isEqualTo(90_000);
	}

	@Test
	void rejectsMissingAuthStatePath() {
		FacebookAuthStateGenerator generator = new FacebookAuthStateGenerator(Map.of(), output());

		assertThatThrownBy(generator::buildRequest)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("FACEBOOK_AUTH_STATE_PATH");
	}

	private PrintStream output() {
		return new PrintStream(new ByteArrayOutputStream());
	}
}
