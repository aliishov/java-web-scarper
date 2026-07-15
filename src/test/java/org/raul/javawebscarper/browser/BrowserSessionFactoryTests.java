package org.raul.javawebscarper.browser;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class BrowserSessionFactoryTests {

	@Test
	void rejectsMissingStorageStateBeforeStartingBrowser() {
		BrowserEngine browserEngine = mock(BrowserEngine.class);
		BrowserSessionFactory factory = new BrowserSessionFactory(browserEngine, new BrowserEngineProperties());

		assertThatThrownBy(() -> factory.createSession(BrowserSessionOptions.withStorageState(Path.of("missing-x-storage-state.json"))))
				.isInstanceOf(BrowserEngineException.class)
				.hasMessage("Configured browser storage state file does not exist");
		verifyNoInteractions(browserEngine);
	}
}
