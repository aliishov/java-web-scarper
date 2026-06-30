package org.raul.javawebscarper.mapper;

import org.raul.javawebscarper.dto.request.source.CreateSourceRequestDTO;
import org.raul.javawebscarper.dto.request.source.UpdateSourceRequestDTO;
import org.raul.javawebscarper.dto.response.source.SourceResponseDTO;
import org.raul.javawebscarper.model.Source;
import org.springframework.stereotype.Component;

@Component
public class SourceMapper {

	public Source toEntity(CreateSourceRequestDTO request) {
		return Source.builder()
				.code(normalize(request.code()))
				.name(request.name().trim())
				.type(request.type())
				.baseUrl(request.baseUrl().trim())
				.enabled(request.enabled() == null || request.enabled())
				.build();
	}

	public void updateEntity(Source source, UpdateSourceRequestDTO request) {
		source.setCode(normalize(request.code()));
		source.setName(request.name().trim());
		source.setType(request.type());
		source.setBaseUrl(request.baseUrl().trim());
		source.setEnabled(request.enabled() == null || request.enabled());
	}

	public SourceResponseDTO toResponse(Source source) {
		return new SourceResponseDTO(
				source.getId(),
				source.getCode(),
				source.getName(),
				source.getType(),
				source.getBaseUrl(),
				source.isEnabled(),
				source.getCreatedAt(),
				source.getUpdatedAt()
		);
	}

	private String normalize(String value) {
		return value.trim().toLowerCase();
	}
}
