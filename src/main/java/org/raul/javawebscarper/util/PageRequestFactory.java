package org.raul.javawebscarper.util;

import org.raul.javawebscarper.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequestFactory {

	private static final int MAX_PAGE_SIZE = 100;

	private PageRequestFactory() {
	}

	public static Pageable create(int page, int size, String sortBy, String direction) {
		if (page < 0) {
			throw new BadRequestException("Page index must not be negative");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new BadRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
		}

		Sort.Direction sortDirection = parseDirection(direction);
		return PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
	}

	private static Sort.Direction parseDirection(String direction) {
		try {
			return Sort.Direction.fromString(direction);
		} catch (IllegalArgumentException exception) {
			throw new BadRequestException("Sort direction must be ASC or DESC");
		}
	}
}
