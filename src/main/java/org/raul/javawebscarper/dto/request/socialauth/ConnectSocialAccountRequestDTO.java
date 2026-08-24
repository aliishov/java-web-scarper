package org.raul.javawebscarper.dto.request.socialauth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials are write-only and are used once to create a browser storage state. */
public record ConnectSocialAccountRequestDTO(
		@JsonAlias({"email", "username"})
		@NotBlank @Size(max = 320) String login,
		@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
		@NotBlank @Size(min = 6, max = 256) String password
) {
}
