package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.api.keyword.KeywordRequest;
import org.raul.javawebscarper.api.keyword.KeywordResponse;
import org.raul.javawebscarper.model.Keyword;
import org.springframework.stereotype.Component;

@Component
public class KeywordMapper {

	public Keyword toEntity(KeywordRequest request) {
		return Keyword.builder()
				.word(normalize(request.word()))
				.enabled(request.enabled() == null || request.enabled())
				.build();
	}

	public void updateEntity(Keyword keyword, KeywordRequest request) {
		keyword.setWord(normalize(request.word()));
		keyword.setEnabled(request.enabled() == null || request.enabled());
	}

	public KeywordResponse toResponse(Keyword keyword) {
		return new KeywordResponse(
				keyword.getId(),
				keyword.getWord(),
				keyword.isEnabled(),
				keyword.getCreatedAt(),
				keyword.getUpdatedAt()
		);
	}

	private String normalize(String value) {
		return value.trim().toLowerCase();
	}
}
