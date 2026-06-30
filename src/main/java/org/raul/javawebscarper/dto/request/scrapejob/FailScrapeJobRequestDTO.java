package org.raul.javawebscarper.dto.request.scrapejob;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailScrapeJobRequestDTO(
		@NotBlank
		@Size(max = 4000)
		String errorMessage
) {
}
