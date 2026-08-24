package org.raul.javawebscarper.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.raul.javawebscarper.auth.AuthStateStatus;
import org.raul.javawebscarper.auth.SocialAuthCredentials;
import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthResult;
import org.raul.javawebscarper.auth.SocialAuthStateService;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.auth.handler.SocialLoginHandler;

import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SocialAuthManagementServiceTests {
	@Test
	void connectPassesCredentialsFromTheUiToThePlatformHandler() {
		SocialAuthProperties properties = new SocialAuthProperties();
		SocialAuthStateValidator validator = mock(SocialAuthStateValidator.class);
		SocialAuthStateService stateService = new SocialAuthStateService();
		SocialLoginHandler handler = mock(SocialLoginHandler.class);
		SocialAuthCredentials credentials = new SocialAuthCredentials("account@example.com", "secret-password");
		SocialAuthResult expected = SocialAuthResult.of(SocialPlatform.X, AuthStateStatus.VALID, "connected");
		when(handler.platform()).thenReturn(SocialPlatform.X);
		when(handler.loginAndSaveState(properties.getX(), credentials)).thenReturn(expected);

		SocialAuthManagementService service = new SocialAuthManagementService(
				properties, validator, stateService, List.of(handler));
		SocialAuthResult result = service.connect(SocialPlatform.X, credentials);

		assertThat(result).isEqualTo(expected);
		assertThat(stateService.status(SocialPlatform.X)).isEqualTo(expected);
		verify(handler).loginAndSaveState(properties.getX(), credentials);
	}

	@Test
	void statusesReportMissingLocalSessionsWithoutValidation(@TempDir Path stateDirectory) {
		SocialAuthProperties properties = new SocialAuthProperties();
		properties.setStateDir(stateDirectory);
		SocialAuthStateValidator validator = mock(SocialAuthStateValidator.class);
		SocialAuthStateService stateService = new SocialAuthStateService();
		SocialAuthManagementService service = new SocialAuthManagementService(
				properties, validator, stateService, List.of());

		List<SocialAuthResult> statuses = service.statuses();

		assertThat(statuses).allMatch(result -> result.status() == AuthStateStatus.MISSING);
		verifyNoInteractions(validator);
	}

	@Test
	void statusesRestorePersistedSessionAfterBackendRestartWithoutValidation(@TempDir Path stateDirectory) throws Exception {
		SocialAuthProperties properties = new SocialAuthProperties();
		properties.setStateDir(stateDirectory);
		Files.writeString(properties.statePath(SocialPlatform.INSTAGRAM), "{\"cookies\":[]}");
		SocialAuthStateValidator validator = mock(SocialAuthStateValidator.class);
		SocialAuthManagementService service = new SocialAuthManagementService(
				properties, validator, new SocialAuthStateService(), List.of());

		SocialAuthResult instagram = service.statuses().stream()
				.filter(result -> result.platform() == SocialPlatform.INSTAGRAM)
				.findFirst()
				.orElseThrow();

		assertThat(instagram.status()).isEqualTo(AuthStateStatus.VALID);
		assertThat(instagram.message()).contains("Saved authentication session");
		verifyNoInteractions(validator);
	}
}
