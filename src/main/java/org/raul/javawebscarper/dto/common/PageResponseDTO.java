package org.raul.javawebscarper.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponseDTO<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {

	public static <T, R> PageResponseDTO<R> from(Page<T> page, Function<T, R> mapper) {
		return new PageResponseDTO<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast()
		);
	}
}
