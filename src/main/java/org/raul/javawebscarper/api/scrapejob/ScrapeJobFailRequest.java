package org.raul.javawebscarper.api.scrapejob;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScrapeJobFailRequest(
		@NotBlank
		@Size(max = 4000)
		String errorMessage
) {
}
