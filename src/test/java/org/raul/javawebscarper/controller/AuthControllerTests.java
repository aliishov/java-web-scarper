package org.raul.javawebscarper.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.dto.request.auth.LoginRequestDTO;
import org.raul.javawebscarper.dto.request.auth.RefreshTokenRequestDTO;
import org.raul.javawebscarper.dto.request.auth.SignupRequestDTO;
import org.raul.javawebscarper.dto.response.auth.AuthTokenResponseDTO;
import org.raul.javawebscarper.dto.response.auth.SignupResponseDTO;
import org.raul.javawebscarper.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

	@Mock
	private AuthService authService;

	private AuthController controller;

	@BeforeEach
	void setUp() {
		controller = new AuthController(authService);
	}

	@Test
	void signupDelegatesWhetherCallerIsASuperAdmin() {
		SignupRequestDTO request = new SignupRequestDTO("new-admin", "long-enough-password");
		SignupResponseDTO created = new SignupResponseDTO(1L);
		var authentication = new UsernamePasswordAuthenticationToken(
				"super-admin",
				null,
				List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
		);
		when(authService.signup(request, true)).thenReturn(created);

		ResponseEntity<BaseResponseDTO<SignupResponseDTO>> response = controller.signup(request, authentication);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getData()).isEqualTo(created);
		verify(authService).signup(request, true);
	}

	@Test
	void loginReturnsTokens() {
		LoginRequestDTO request = new LoginRequestDTO("admin", "password");
		AuthTokenResponseDTO tokens = new AuthTokenResponseDTO("access", "refresh");
		when(authService.login(request)).thenReturn(tokens);

		ResponseEntity<BaseResponseDTO<AuthTokenResponseDTO>> response = controller.login(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getData()).isEqualTo(tokens);
		verify(authService).login(request);
	}

	@Test
	void refreshAndLogoutDelegateTheProvidedRefreshToken() {
		RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("refresh-token");
		AuthTokenResponseDTO tokens = new AuthTokenResponseDTO("new-access", "new-refresh");
		when(authService.refresh(request)).thenReturn(tokens);

		ResponseEntity<BaseResponseDTO<AuthTokenResponseDTO>> refreshResponse = controller.refresh(request);
		ResponseEntity<BaseResponseDTO<Void>> logoutResponse = controller.logout(request);

		assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(refreshResponse.getBody()).isNotNull();
		assertThat(refreshResponse.getBody().getData()).isEqualTo(tokens);
		assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(authService).refresh(request);
		verify(authService).logout(request);
	}
}
