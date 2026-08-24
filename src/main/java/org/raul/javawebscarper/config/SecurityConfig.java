package org.raul.javawebscarper.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/api/auth/login",
								"/api/auth/refresh",
								"/api/auth/logout",
								"/api/auth/signup",
								"/v3/api-docs/**",
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/actuator/health",
								"/actuator/info"
						).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/admins/**")
						.hasAnyRole("ADMIN", "SUPER_ADMIN")
						.requestMatchers("/api/admins/**").hasRole("SUPER_ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/social-auth/**")
						.hasAnyRole("ADMIN", "SUPER_ADMIN")
						.requestMatchers("/api/social-auth/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
						.requestMatchers("/api/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint((request, response, exception) -> {
							response.setStatus(HttpStatus.UNAUTHORIZED.value());
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							objectMapper.writeValue(response.getOutputStream(),
									BaseResponseDTO.error("Authentication is required or the access token is invalid"));
						}))
				.exceptionHandling(handling -> handling.accessDeniedHandler((request, response, exception) -> {
					response.setStatus(HttpStatus.FORBIDDEN.value());
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					objectMapper.writeValue(response.getOutputStream(), BaseResponseDTO.error("Access denied"));
				}))
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
	}

	@Bean
	public JwtDecoder jwtDecoder(JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		return decoder;
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		authoritiesConverter.setAuthoritiesClaimName("roles");
		authoritiesConverter.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return converter;
	}

	private SecretKey secretKey(JwtProperties properties) {
		byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
		return new SecretKeySpec(bytes, "HmacSHA256");
	}
}
