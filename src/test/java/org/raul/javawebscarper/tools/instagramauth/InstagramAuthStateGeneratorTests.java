package org.raul.javawebscarper.tools.instagramauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstagramAuthStateGeneratorTests {

	@TempDir
	private Path tempDir;

	@Test
	void buildsRequestFromEnvironment() {
		InstagramAuthStateGenerator generator = new InstagramAuthStateGenerator(
				Map.of(
						"INSTAGRAM_AUTH_STATE_PATH", "playwright/.auth/instagram-storage-state.json",
						"INSTAGRAM_MANUAL_VERIFICATION_TIMEOUT_MS", "90000",
						"INSTAGRAM_LOGIN_URL", "https://www.instagram.com/accounts/login/",
						"INSTAGRAM_AUTH_VERIFICATION_URL", "https://www.instagram.com/",
						"INSTAGRAM_LOCALE", "az-AZ",
						"INSTAGRAM_TIMEZONE_ID", "Asia/Baku"
				),
				output()
		);

		InstagramAuthStateGenerator.InstagramAuthStateRequest request = generator.buildRequest();

		assertThat(request.authStatePath()).isAbsolute();
		assertThat(request.authStatePath().toString().replace('\\', '/')).endsWith("playwright/.auth/instagram-storage-state.json");
		assertThat(request.manualVerificationTimeoutMs()).isEqualTo(90_000);
		assertThat(request.loginUrl()).isEqualTo("https://www.instagram.com/accounts/login/");
		assertThat(request.authVerificationUrl()).isEqualTo("https://www.instagram.com/");
		assertThat(request.locale()).isEqualTo("az-AZ");
		assertThat(request.timezoneId()).isEqualTo("Asia/Baku");
	}

	@Test
	void rejectsMissingAuthStatePath() {
		InstagramAuthStateGenerator generator = new InstagramAuthStateGenerator(Map.of(), output());

		assertThatThrownBy(generator::buildRequest)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("INSTAGRAM_AUTH_STATE_PATH");
	}

	@Test
	void createsParentDirectoryForAuthStatePath() {
		InstagramAuthStateGenerator generator = new InstagramAuthStateGenerator(Map.of(), output());
		Path authStatePath = tempDir.resolve("nested/auth/instagram-storage-state.json");

		generator.validateAuthStatePath(authStatePath);

		assertThat(Files.isDirectory(authStatePath.getParent())).isTrue();
	}

	@Test
	void rejectsDirectoryAsAuthStatePath() throws Exception {
		InstagramAuthStateGenerator generator = new InstagramAuthStateGenerator(Map.of(), output());
		Path directoryPath = tempDir.resolve("directory.json");
		Files.createDirectory(directoryPath);

		assertThatThrownBy(() -> generator.validateAuthStatePath(directoryPath))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must point to a file");
	}

	@Test
	void rejectsNonJsonAuthStatePath() {
		InstagramAuthStateGenerator generator = new InstagramAuthStateGenerator(Map.of(), output());

		assertThatThrownBy(() -> generator.validateAuthStatePath(tempDir.resolve("instagram-storage-state.txt")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("JSON file");
	}

	@Test
	void rejectsRepositorySourceAndConfigPaths() {
		InstagramAuthStateGenerator generator = new InstagramAuthStateGenerator(Map.of(), output());

		assertThatThrownBy(() -> generator.validateAuthStatePath(Path.of("src/main/resources/instagram-storage-state.json")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not point to source");
		assertThatThrownBy(() -> generator.validateAuthStatePath(Path.of(".git/instagram-storage-state.json")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not point to source");
	}

	@Test
	void rejectsInvalidTimeoutValue() {
		InstagramAuthStateGenerator generator = new InstagramAuthStateGenerator(
				Map.of(
						"INSTAGRAM_AUTH_STATE_PATH", tempDir.resolve("instagram-storage-state.json").toString(),
						"INSTAGRAM_MANUAL_VERIFICATION_TIMEOUT_MS", "not-a-number"
				),
				output()
		);

		assertThatThrownBy(generator::buildRequest)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("INSTAGRAM_MANUAL_VERIFICATION_TIMEOUT_MS");
	}

	private PrintStream output() {
		return new PrintStream(new ByteArrayOutputStream());
	}
}
