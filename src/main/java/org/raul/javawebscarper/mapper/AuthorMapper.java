package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.dto.request.author.CreateAuthorRequestDTO;
import org.raul.javawebscarper.dto.request.author.UpdateAuthorRequestDTO;
import org.raul.javawebscarper.dto.response.author.AuthorResponseDTO;
import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Source;

public final class AuthorMapper {

	private AuthorMapper() {
	}

	public static Author toEntity(CreateAuthorRequestDTO request, Source source) {
		return Author.builder()
				.source(source)
				.externalId(nullIfBlank(request.externalId()))
				.username(request.username().trim())
				.profileUrl(nullIfBlank(request.profileUrl()))
				.build();
	}

	public static void updateEntity(Author author, UpdateAuthorRequestDTO request, Source source) {
		author.setSource(source);
		author.setExternalId(nullIfBlank(request.externalId()));
		author.setUsername(request.username().trim());
		author.setProfileUrl(nullIfBlank(request.profileUrl()));
	}

	public static AuthorResponseDTO toResponse(Author author) {
		return new AuthorResponseDTO(
				author.getId(),
				author.getSource().getId(),
				author.getSource().getCode(),
				author.getUsername(),
				author.getExternalId(),
				author.getProfileUrl(),
				author.getCreatedAt(),
				author.getUpdatedAt()
		);
	}

	private static String nullIfBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
