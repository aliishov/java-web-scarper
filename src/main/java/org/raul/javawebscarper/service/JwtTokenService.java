package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.config.JwtProperties;
import org.raul.javawebscarper.model.Admin;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;

	public String createAccessToken(Admin admin) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(properties.accessTokenTtl()))
				.subject(admin.getUsername())
				.claim("adminId", admin.getId())
				.claim("roles", List.of(admin.getRole().name()))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
