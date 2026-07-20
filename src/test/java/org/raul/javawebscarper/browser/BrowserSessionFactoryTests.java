package org.raul.javawebscarper.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class BrowserSessionFactoryTests {

	@TempDir
	private Path tempDir;

	@Test
	void rejectsMissingStorageStateBeforeStartingBrowser() {
		BrowserEngine browserEngine = mock(BrowserEngine.class);
		BrowserSessionFactory factory = new BrowserSessionFactory(browserEngine, new BrowserEngineProperties());

		assertThatThrownBy(() -> factory.createSession(BrowserSessionOptions.withStorageState(Path.of("missing-x-storage-state.json"))))
				.isInstanceOf(BrowserEngineException.class)
				.hasMessage("Configured browser storage state file does not exist");
		verifyNoInteractions(browserEngine);
	}

	@Test
	void rejectsEmptyStorageStateBeforeStartingBrowser() throws IOException {
		BrowserEngine browserEngine = mock(BrowserEngine.class);
		BrowserSessionFactory factory = new BrowserSessionFactory(browserEngine, new BrowserEngineProperties());
		Path emptyState = Files.createFile(tempDir.resolve("storage-state.json"));

		assertThatThrownBy(() -> factory.createSession(BrowserSessionOptions.withStorageState(emptyState)))
				.isInstanceOf(BrowserEngineException.class)
				.hasMessage("Configured browser storage state file is empty");
		verifyNoInteractions(browserEngine);
	}
}
