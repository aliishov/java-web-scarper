package org.raul.javawebscarper.tools.tiktokauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikTokAuthStateGeneratorTests {

	@TempDir
	private Path tempDir;

	@Test
	void buildCommandRequiresExplicitAuthStatePath() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		TikTokAuthStateGenerator generator = new TikTokAuthStateGenerator(Map.of(), new PrintStream(output));

		assertThat(generator.run()).isEqualTo(2);
		assertThat(output.toString()).contains("TIKTOK_AUTH_STATE_PATH environment variable is required");
	}

	@Test
	void buildCommandUsesOnlyStatePathAndNonSecretOptions() {
		Path statePath = tempDir.resolve("nested").resolve("tiktok-storage-state.json");
		Map<String, String> environment = new HashMap<>(Map.of(
				"TIKTOK_AUTH_STATE_PATH", statePath.toString(),
				"TIKTOK_MANUAL_VERIFICATION_TIMEOUT_MS", "90000",
				"TIKTOK_LOGIN_URL", "https://www.tiktok.com/login",
				"TIKTOK_AUTH_VERIFICATION_URL", "https://www.tiktok.com/",
				"TIKTOK_LOCALE", "az-AZ",
				"TIKTOK_TIMEZONE_ID", "Asia/Baku",
				"TIKTOK_LOGIN", "must-not-be-read",
				"TIKTOK_PASSWORD", "must-not-be-read",
				"TIKTOK_OTP", "must-not-be-read"
		));

		TikTokAuthStateCommand command = new TikTokAuthStateGenerator(environment, new PrintStream(new ByteArrayOutputStream()))
				.buildCommand();

		assertThat(command.authStatePath()).isEqualTo(statePath.toAbsolutePath().normalize());
		assertThat(command.manualVerificationTimeoutMs()).isEqualTo(90_000);
		assertThat(command.locale()).isEqualTo("az-AZ");
		assertThat(command.timezoneId()).isEqualTo("Asia/Baku");
		assertThat(Files.isDirectory(statePath.getParent())).isTrue();
	}

	@Test
	void rejectsInvalidOutputPath() {
		Path docsPath = Path.of("docs").resolve("tiktok-storage-state.json");
		Map<String, String> environment = Map.of("TIKTOK_AUTH_STATE_PATH", docsPath.toString());

		assertThatThrownBy(() -> new TikTokAuthStateGenerator(environment, new PrintStream(new ByteArrayOutputStream())).buildCommand())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not point to source, config, docs, or Git metadata files");

		Map<String, String> notJson = Map.of("TIKTOK_AUTH_STATE_PATH", tempDir.resolve("state.txt").toString());
		assertThatThrownBy(() -> new TikTokAuthStateGenerator(notJson, new PrintStream(new ByteArrayOutputStream())).buildCommand())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must point to a JSON file");

		Map<String, String> currentDirectoryFile = Map.of("TIKTOK_AUTH_STATE_PATH", "tiktok-storage-state.json");
		assertThatThrownBy(() -> new TikTokAuthStateGenerator(currentDirectoryFile, new PrintStream(new ByteArrayOutputStream())).buildCommand())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not point to a file in the current directory");
	}

	@Test
	void runDoesNotPrintStatePathOrCredentials() {
		Path statePath = tempDir.resolve("tiktok-storage-state.json");
		Map<String, String> environment = Map.of(
				"TIKTOK_AUTH_STATE_PATH", statePath.toString(),
				"TIKTOK_PASSWORD", "very-secret"
		);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		TikTokAuthStateGenerator generator = new TestableGenerator(environment, new PrintStream(output));

		assertThat(generator.run()).isZero();

		String text = output.toString();
		assertThat(text).contains("TikTok authentication state was saved successfully.");
		assertThat(text).doesNotContain(statePath.toString());
		assertThat(text).doesNotContain("very-secret");
	}

	private static class TestableGenerator extends TikTokAuthStateGenerator {

		TestableGenerator(Map<String, String> environment, PrintStream output) {
			super(environment, output);
		}

		@Override
		protected void createStorageState(TikTokAuthStateCommand command) {
			// Avoid live Playwright/TikTok in unit tests.
		}
	}
}
