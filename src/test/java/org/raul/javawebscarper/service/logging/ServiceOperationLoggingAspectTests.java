package org.raul.javawebscarper.service.logging;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.dto.response.keyword.KeywordResponseDTO;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceOperationLoggingAspectTests {

	@Test
	void redactsAllStringValuesAndDoesNotRenderDtoContents() {
		CredentialsRequest login = new CredentialsRequest("admin", "secret-password");

		assertThat(ServiceOperationLoggingAspect.summarizeValue("sensitive-value"))
				.isEqualTo("<redacted-string length=15>");
		assertThat(ServiceOperationLoggingAspect.summarizeValue(login))
				.isEqualTo("CredentialsRequest")
				.doesNotContain("admin", "secret-password");
	}

	@Test
	void summarizesOperationalValuesWithoutRenderingCollectionContents() {
		Source source = Source.builder().id(7).code("OXU_AZ").build();
		Keyword keyword = Keyword.builder().id(9).word("private keyword").language(Language.AZ).build();

		assertThat(ServiceOperationLoggingAspect.summarizeValue(source))
				.isEqualTo("Source[id=7, code=OXU_AZ]");
		assertThat(ServiceOperationLoggingAspect.summarizeValue(keyword))
				.isEqualTo("Keyword[id=9, language=AZ]")
				.doesNotContain("private keyword");
		assertThat(ServiceOperationLoggingAspect.summarizeValue(List.of("secret")))
				.isEqualTo("List12[size=1]");
		assertThat(ServiceOperationLoggingAspect.summarizeValue(Map.of("token", "secret")))
				.startsWith("Map")
				.endsWith("[size=1]")
				.doesNotContain("token", "secret");
		assertThat(ServiceOperationLoggingAspect.summarizeValue(PageRequest.of(2, 25)))
				.contains("page=2", "size=25");
		assertThat(ServiceOperationLoggingAspect.summarizeValue(UUID.fromString(
				"5d94e5d8-94b2-4c46-a040-e82964a68d32"
		))).isEqualTo("5d94e5d8-94b2-4c46-a040-e82964a68d32");
	}

	@Test
	void includesParameterNamesInArgumentSummary() {
		String summary = ServiceOperationLoggingAspect.summarizeArguments(
				new String[]{"id", "request"},
				new Object[]{42, new CredentialsRequest("admin", "secret")}
		);

		assertThat(summary)
				.isEqualTo("id=42, request=CredentialsRequest")
				.doesNotContain("admin", "secret");
	}

	@Test
	void includesOnlySafeRecordResultFields() {
		KeywordResponseDTO keyword = new KeywordResponseDTO(
				9,
				"private keyword",
				Language.AZ,
				true,
				OffsetDateTime.parse("2026-07-30T10:00:00+04:00"),
				OffsetDateTime.parse("2026-07-30T10:00:00+04:00")
		);
		TokenPairResponse tokens = new TokenPairResponse("access-secret", "refresh-secret");

		assertThat(ServiceOperationLoggingAspect.summarizeValue(keyword))
				.isEqualTo("KeywordResponseDTO[id=9, enabled=true]")
				.doesNotContain("private keyword");
		assertThat(ServiceOperationLoggingAspect.summarizeValue(tokens))
				.isEqualTo("TokenPairResponse")
				.doesNotContain("access-secret", "refresh-secret");
	}

	private record CredentialsRequest(String username, String password) {
	}

	private record TokenPairResponse(String accessToken, String refreshToken) {
	}
}
