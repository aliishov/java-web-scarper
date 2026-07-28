package org.raul.javawebscarper.auth;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.auth.handler.SocialLoginHandler;

import java.util.List;

import static org.mockito.Mockito.*;

class SocialAuthBootstrapServiceTests {
	@Test
	void validStatesDoNotInvokeLoginHandler() {
		SocialAuthProperties properties = propertiesOnlyXEnabled();
		SocialAuthStateValidator validator = mock(SocialAuthStateValidator.class);
		SocialAuthStateService states = new SocialAuthStateService();
		SocialLoginHandler handler = mock(SocialLoginHandler.class);
		when(handler.platform()).thenReturn(SocialPlatform.X);
		for (SocialPlatform platform : SocialPlatform.values()) {
			when(validator.validate(platform)).thenReturn(SocialAuthResult.of(platform,
					platform == SocialPlatform.X ? AuthStateStatus.VALID : AuthStateStatus.DISABLED, "ok"));
		}

		new SocialAuthBootstrapService(properties, validator, states, List.of(handler)).bootstrap();

		verify(handler, never()).loginAndSaveState(any());
	}

	@Test
	void missingStateInvokesLoginHandler() {
		SocialAuthProperties properties = propertiesOnlyXEnabled();
		SocialAuthStateValidator validator = mock(SocialAuthStateValidator.class);
		SocialAuthStateService states = new SocialAuthStateService();
		SocialLoginHandler handler = mock(SocialLoginHandler.class);
		when(handler.platform()).thenReturn(SocialPlatform.X);
		when(handler.loginAndSaveState(any())).thenReturn(
				SocialAuthResult.of(SocialPlatform.X, AuthStateStatus.LOGIN_FAILED, "failed"));
		for (SocialPlatform platform : SocialPlatform.values()) {
			when(validator.validate(platform)).thenReturn(SocialAuthResult.of(platform,
					platform == SocialPlatform.X ? AuthStateStatus.MISSING : AuthStateStatus.DISABLED, "status"));
		}

		new SocialAuthBootstrapService(properties, validator, states, List.of(handler)).bootstrap();

		verify(handler).loginAndSaveState(properties.getX());
	}

	private SocialAuthProperties propertiesOnlyXEnabled() {
		SocialAuthProperties properties = new SocialAuthProperties();
		for (SocialPlatform platform : SocialPlatform.values()) properties.account(platform).setEnabled(platform == SocialPlatform.X);
		return properties;
	}
}
