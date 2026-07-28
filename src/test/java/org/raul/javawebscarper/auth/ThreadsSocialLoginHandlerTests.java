package org.raul.javawebscarper.auth;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.auth.handler.ThreadsSocialLoginHandler;
import org.raul.javawebscarper.browser.BrowserEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ThreadsSocialLoginHandlerTests {
	@Test
	void handlerIsRegisteredForThreads() {
		ThreadsSocialLoginHandler handler = new ThreadsSocialLoginHandler(
				mock(BrowserEngine.class), new SocialAuthProperties(), mock(SocialAuthStateValidator.class));
		assertThat(handler.platform()).isEqualTo(SocialPlatform.THREADS);
	}
}
