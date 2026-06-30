package org.raul.javawebscarper.dto.request.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UpdatePostRequestDTO(
		@NotNull
		Integer sourceId,

		@NotNull
		UUID authorId,

		@Size(max = 255)
		String externalPostId,

		@NotBlank
		@URL
		String postUrl,

		@NotNull
		@PastOrPresent
		OffsetDateTime postDate,

		@PastOrPresent
		OffsetDateTime scrapedAt,

		@NotBlank
		String text,

		@NotBlank
		@Size(max = 128)
		String textHash,

		@Size(max = 16)
		String language,

		@Valid
		List<PostMediaRequestDTO> media,

		@Valid
		List<PostKeywordRequestDTO> keywords
) {
}
