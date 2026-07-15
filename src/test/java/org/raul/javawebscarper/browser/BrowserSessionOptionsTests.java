package org.raul.javawebscarper.browser;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserSessionOptionsTests {

	@Test
	void createsStorageStateOptions() {
		Path path = Path.of("playwright/.auth/x-storage-state.json");

		BrowserSessionOptions options = BrowserSessionOptions.withStorageState(path);

		assertThat(options.storageStatePath()).isEqualTo(path);
		assertThat(options.extraHttpHeaders()).isEmpty();
	}
}
