package org.raul.javawebscarper.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.dto.request.auth.LoginRequestDTO;
import org.raul.javawebscarper.dto.request.auth.RefreshTokenRequestDTO;
import org.raul.javawebscarper.dto.request.auth.SignupRequestDTO;
import org.raul.javawebscarper.dto.response.auth.AuthTokenResponseDTO;
import org.raul.javawebscarper.dto.response.auth.SignupResponseDTO;
import org.raul.javawebscarper.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final String TOKEN_RESPONSE_EXAMPLE = """
			{
			  "success": true,
			  "message": "Login successful",
			  "data": {
			    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
			    "refreshToken": "Y7V8x5...secure-refresh-token"
			  },
			  "timestamp": "2026-07-23T16:08:14+04:00"
			}
			""";

	private static final String SIGNUP_RESPONSE_EXAMPLE = """
			{
			  "success": true,
			  "message": "Admin account created successfully",
			  "data": {
			    "id": 1
			  },
			  "timestamp": "2026-07-23T16:08:14+04:00"
			}
			""";

	private final AuthService authService;

	@PostMapping("/signup")
	@Operation(summary = "Create an admin account")
	@ApiResponse(
			responseCode = "201",
			description = "Admin account created",
			content = @Content(examples = @ExampleObject(value = SIGNUP_RESPONSE_EXAMPLE))
	)
	@ApiResponse(responseCode = "400", description = "The request is invalid")
	public ResponseEntity<BaseResponseDTO<SignupResponseDTO>> signup(
			@Valid @RequestBody SignupRequestDTO request,
			Authentication authentication
	) {
		boolean superAdmin = authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(BaseResponseDTO.success(
						authService.signup(request, superAdmin),
						"Admin account created successfully"));
	}

	@PostMapping("/login")
	@Operation(summary = "Log in as admin")
	@ApiResponse(
			responseCode = "200",
			description = "Login successful",
			content = @Content(examples = @ExampleObject(value = TOKEN_RESPONSE_EXAMPLE))
	)
	@ApiResponse(responseCode = "401", description = "Username or password is incorrect")
	public ResponseEntity<BaseResponseDTO<AuthTokenResponseDTO>> login(
			@Valid @RequestBody LoginRequestDTO request
	) {
		return ResponseEntity.ok(BaseResponseDTO.success(authService.login(request), "Login successful"));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate the refresh token and issue new tokens")
	@ApiResponse(
			responseCode = "200",
			description = "Tokens refreshed",
			content = @Content(examples = @ExampleObject(value = TOKEN_RESPONSE_EXAMPLE))
	)
	@ApiResponse(responseCode = "401", description = "Refresh token is invalid or expired")
	public ResponseEntity<BaseResponseDTO<AuthTokenResponseDTO>> refresh(
			@Valid @RequestBody RefreshTokenRequestDTO request
	) {
		return ResponseEntity.ok(BaseResponseDTO.success(authService.refresh(request), "Tokens refreshed successfully"));
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke a refresh token")
	@ApiResponse(responseCode = "200", description = "Logout successful")
	@ApiResponse(responseCode = "401", description = "Refresh token is invalid")
	public ResponseEntity<BaseResponseDTO<Void>> logout(
			@Valid @RequestBody RefreshTokenRequestDTO request
	) {
		authService.logout(request);
		return ResponseEntity.ok(BaseResponseDTO.success("Logout successful"));
	}
}
