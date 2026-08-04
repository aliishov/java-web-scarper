package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.config.JwtProperties;
import org.raul.javawebscarper.dto.request.auth.LoginRequestDTO;
import org.raul.javawebscarper.dto.request.auth.RefreshTokenRequestDTO;
import org.raul.javawebscarper.dto.request.auth.SignupRequestDTO;
import org.raul.javawebscarper.dto.response.auth.AuthTokenResponseDTO;
import org.raul.javawebscarper.dto.response.auth.SignupResponseDTO;
import org.raul.javawebscarper.exception.UnauthorizedException;
import org.raul.javawebscarper.exception.ForbiddenException;
import org.raul.javawebscarper.model.Admin;
import org.raul.javawebscarper.model.RefreshToken;
import org.raul.javawebscarper.model.enumerated.AdminRole;
import org.raul.javawebscarper.repository.AdminRepository;
import org.raul.javawebscarper.repository.RefreshTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final AdminRepository adminRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final JwtProperties properties;

	@Transactional
	public SignupResponseDTO signup(SignupRequestDTO request, boolean requestedBySuperAdmin) {
		boolean firstAdmin = adminRepository.count() == 0;
		if (!firstAdmin && !requestedBySuperAdmin) {
			throw new ForbiddenException("Only a super admin can create another admin");
		}
		Admin admin = Admin.builder()
				.username(normalizeUsername(request.username()))
				.passwordHash(passwordEncoder.encode(request.password()))
				.role(firstAdmin ? AdminRole.SUPER_ADMIN : AdminRole.ADMIN)
				.build();
		Admin savedAdmin = adminRepository.saveAndFlush(admin);
		return new SignupResponseDTO(savedAdmin.getId());
	}

	@Transactional
	public AuthTokenResponseDTO login(LoginRequestDTO request) {
		Admin admin = adminRepository.findByUsernameIgnoreCase(normalizeUsername(request.username()))
				.orElseThrow(this::invalidCredentials);
		if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
			throw invalidCredentials();
		}
		return issueTokens(admin);
	}

	@Transactional
	public AuthTokenResponseDTO refresh(RefreshTokenRequestDTO request) {
		RefreshToken storedToken = findTokenForUpdate(request.refreshToken());
		OffsetDateTime now = OffsetDateTime.now();
		if (storedToken.getRevokedAt() != null || !storedToken.getExpiresAt().isAfter(now)) {
			throw new UnauthorizedException("Refresh token is invalid or expired");
		}
		storedToken.setRevokedAt(now);
		return issueTokens(storedToken.getAdmin());
	}

	@Transactional
	public void logout(RefreshTokenRequestDTO request) {
		RefreshToken storedToken = findTokenForUpdate(request.refreshToken());
		if (storedToken.getRevokedAt() == null) {
			storedToken.setRevokedAt(OffsetDateTime.now());
		}
	}

	private AuthTokenResponseDTO issueTokens(Admin admin) {
		String rawRefreshToken = generateRefreshToken();
		RefreshToken refreshToken = RefreshToken.builder()
				.admin(admin)
				.tokenHash(hash(rawRefreshToken))
				.expiresAt(OffsetDateTime.now().plus(properties.refreshTokenTtl()))
				.build();
		refreshTokenRepository.save(refreshToken);
		return new AuthTokenResponseDTO(
				jwtTokenService.createAccessToken(admin),
				rawRefreshToken
		);
	}

	private RefreshToken findTokenForUpdate(String rawToken) {
		return refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
				.orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));
	}

	private String generateRefreshToken() {
		byte[] bytes = new byte[64];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String normalizeUsername(String username) {
		return username.trim().toLowerCase(Locale.ROOT);
	}

	private UnauthorizedException invalidCredentials() {
		return new UnauthorizedException("Username or password is incorrect");
	}
}
