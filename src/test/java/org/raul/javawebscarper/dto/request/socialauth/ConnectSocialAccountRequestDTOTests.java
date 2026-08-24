package org.raul.javawebscarper.dto.request.socialauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectSocialAccountRequestDTOTests {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void acceptsTheEmailFieldSentByTheConnectDialog() throws Exception {
		ConnectSocialAccountRequestDTO request = objectMapper.readValue(
				"{\"email\":\"account@example.com\",\"password\":\"secret-password\"}",
				ConnectSocialAccountRequestDTO.class);

		assertThat(request.login()).isEqualTo("account@example.com");
		assertThat(request.password()).isEqualTo("secret-password");
	}

	@Test
	void continuesToAcceptLoginForPlatformUsernames() throws Exception {
		ConnectSocialAccountRequestDTO request = objectMapper.readValue(
				"{\"login\":\"account_name\",\"password\":\"secret-password\"}",
				ConnectSocialAccountRequestDTO.class);

		assertThat(request.login()).isEqualTo("account_name");
	}
}
