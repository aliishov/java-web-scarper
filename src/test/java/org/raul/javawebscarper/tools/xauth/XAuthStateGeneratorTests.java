package org.raul.javawebscarper.tools.xauth;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class XAuthStateGeneratorTests {

	@Test
	void buildsRequestFromEnvironmentWithoutPrintingCredentials() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		XAuthStateGenerator generator = new XAuthStateGenerator(
				Map.of(
						"X_LOGIN", "user@example.test",
						"X_USERNAME", "example_user",
						"X_PASSWORD", "test-secret-value",
						"X_AUTH_STATE_PATH", "playwright/.auth/x-storage-state.json"
				),
				null,
				new PrintStream(output)
		);

		XAuthStateGenerator.XAuthStateRequest request = generator.buildRequest(new String[0]);

		assertThat(request.login()).isEqualTo("user@example.test");
		assertThat(request.username()).isEqualTo("example_user");
		assertThat(request.password()).containsExactly("test-secret-value".toCharArray());
		assertThat(request.authStatePath()).isEqualTo(Path.of("playwright/.auth/x-storage-state.json"));
		assertThat(request.interactiveOnly()).isFalse();
		assertThat(output.toString()).doesNotContain("test-secret-value").doesNotContain("user@example.test");
		request.clearPassword();
		assertThat(request.password()).containsOnly('\0');
	}

	@Test
	void interactiveRequestRequiresOnlyOutputPath() {
		XAuthStateGenerator generator = new XAuthStateGenerator(
				Map.of("X_AUTH_STATE_PATH", "playwright/.auth/x-storage-state.json"),
				null,
				new PrintStream(new ByteArrayOutputStream())
		);

		XAuthStateGenerator.XAuthStateRequest request = generator.buildRequest(new String[]{"--interactive-only"});

		assertThat(request.login()).isNull();
		assertThat(request.password()).isNull();
		assertThat(request.authStatePath()).isEqualTo(Path.of("playwright/.auth/x-storage-state.json"));
		assertThat(request.interactiveOnly()).isTrue();
	}

	@Test
	void missingLoginReturnsSafeErrorWithoutPrintingPassword() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		XAuthStateGenerator generator = new XAuthStateGenerator(
				Map.of(
						"X_PASSWORD", "test-secret-value",
						"X_AUTH_STATE_PATH", "playwright/.auth/x-storage-state.json"
				),
				null,
				new PrintStream(output)
		);

		int exitCode = generator.run(new String[0]);

		assertThat(exitCode).isEqualTo(2);
		assertThat(output.toString())
				.contains("X_LOGIN environment variable is required.")
				.doesNotContain("test-secret-value");
	}

	@Test
	void missingPasswordRequiresEnvironmentWhenConsoleIsUnavailable() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		XAuthStateGenerator generator = new XAuthStateGenerator(
				Map.of(
						"X_LOGIN", "user@example.test",
						"X_AUTH_STATE_PATH", "playwright/.auth/x-storage-state.json"
				),
				null,
				new PrintStream(output)
		);

		int exitCode = generator.run(new String[0]);

		assertThat(exitCode).isEqualTo(2);
		assertThat(output.toString()).contains("X_PASSWORD environment variable is required");
	}
}
