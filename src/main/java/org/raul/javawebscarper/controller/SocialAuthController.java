package org.raul.javawebscarper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.auth.SocialAuthCredentials;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.raul.javawebscarper.dto.request.socialauth.ConnectSocialAccountRequestDTO;
import org.raul.javawebscarper.dto.response.socialauth.SocialAuthStatusResponseDTO;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.service.SocialAuthManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/social-auth")
@RequiredArgsConstructor
public class SocialAuthController {
	private final SocialAuthManagementService service;

	@GetMapping
	public ResponseEntity<BaseResponseDTO<List<SocialAuthStatusResponseDTO>>> statuses() {
		return ResponseEntity.ok(BaseResponseDTO.success(service.statuses().stream()
				.map(SocialAuthStatusResponseDTO::from).toList()));
	}

	@PostMapping("/{platform}/verify")
	public ResponseEntity<BaseResponseDTO<SocialAuthStatusResponseDTO>> verify(@PathVariable String platform) {
		return ResponseEntity.ok(BaseResponseDTO.success(SocialAuthStatusResponseDTO.from(service.verify(platform(platform)))));
	}

	@PostMapping("/{platform}/connect")
	public ResponseEntity<BaseResponseDTO<SocialAuthStatusResponseDTO>> connect(
			@PathVariable String platform,
			@Valid @RequestBody(required = false) ConnectSocialAccountRequestDTO request
	) {
		return ResponseEntity.ok(BaseResponseDTO.success(SocialAuthStatusResponseDTO.from(
				service.connect(platform(platform), new SocialAuthCredentials(
						request == null ? null : request.login(), request == null ? null : request.password()))
		), "Social account connection completed"));
	}

	@DeleteMapping("/{platform}")
	public ResponseEntity<BaseResponseDTO<SocialAuthStatusResponseDTO>> disconnect(@PathVariable String platform) {
		return ResponseEntity.ok(BaseResponseDTO.success(SocialAuthStatusResponseDTO.from(service.disconnect(platform(platform))),
				"Social authentication state removed"));
	}

	private SocialPlatform platform(String value) {
		try {
			return SocialPlatform.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new BadRequestException("Unsupported social platform: " + value);
		}
	}
}
