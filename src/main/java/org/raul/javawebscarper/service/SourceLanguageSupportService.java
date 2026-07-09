package org.raul.javawebscarper.service;

import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Source;
import org.springframework.stereotype.Service;

@Service
public class SourceLanguageSupportService {

	public boolean isSupported(Source source, Keyword keyword) {
		return source != null
				&& keyword != null
				&& source.supportsLanguage(keyword.getLanguage());
	}

	public void validateSupported(Source source, Keyword keyword) {
		if (!isSupported(source, keyword)) {
			throw new BadRequestException(
					"Source '%s' does not support keyword language '%s'"
							.formatted(source.getCode(), keyword.getLanguage())
			);
		}
	}
}
