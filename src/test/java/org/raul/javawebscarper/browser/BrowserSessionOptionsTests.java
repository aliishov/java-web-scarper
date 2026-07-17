package org.raul.javawebscarper.browser;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserSessionOptionsTests {

	@Test
	void createsStorageStateOptions() {
		Path path = Path.of("playwright/.auth/x-storage-state.json");

		BrowserSessionOptions options = BrowserSessionOptions.withStorageState(path);

		assertThat(options.storageStatePath()).isEqualTo(path);
		assertThat(options.extraHttpHeaders()).isEmpty();
	}

	@Test
	void defaultsDoNotUseAnyStorageState() {
		BrowserSessionOptions options = BrowserSessionOptions.defaults();

		assertThat(options.storageStatePath()).isNull();
		assertThat(options.locale()).isNull();
		assertThat(options.timezoneId()).isNull();
		assertThat(options.userAgent()).isNull();
		assertThat(options.extraHttpHeaders()).isEmpty();
	}

	@Test
	void canCarryIndependentBrowserContextOptionsWithoutCredentials() {
		Path facebookState = Path.of("playwright/.auth/facebook-storage-state.json");

		BrowserSessionOptions options = new BrowserSessionOptions(
				facebookState,
				"en-US",
				"Asia/Baku",
				null,
				Map.of("Accept-Language", "en-US,en;q=0.9")
		);

		assertThat(options.storageStatePath()).isEqualTo(facebookState);
		assertThat(options.locale()).isEqualTo("en-US");
		assertThat(options.timezoneId()).isEqualTo("Asia/Baku");
		assertThat(options.userAgent()).isNull();
		assertThat(options.extraHttpHeaders()).containsEntry("Accept-Language", "en-US,en;q=0.9");
	}
}
