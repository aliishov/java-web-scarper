package org.raul.javawebscarper.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.raul.javawebscarper.dto.request.scrapejob.CreateScrapeJobRequestDTO;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.Language;
import org.raul.javawebscarper.repository.ScrapeJobRepository;

import java.time.LocalDate;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScrapeJobServiceTests {

	@Mock
	private ScrapeJobRepository scrapeJobRepository;

	@Mock
	private SourceService sourceService;

	@Mock
	private KeywordService keywordService;

	private ScrapeJobService scrapeJobService;

	@BeforeEach
	void setUp() {
		scrapeJobService = new ScrapeJobService(
				scrapeJobRepository,
				sourceService,
				keywordService,
				new SourceLanguageSupportService()
		);
	}

	@Test
	void createRejectsUnsupportedSourceKeywordLanguagePair() {
		Source source = Source.builder()
				.id(1)
				.code("media_az")
				.supportedLanguages(EnumSet.of(Language.RU))
				.build();
		Keyword keyword = Keyword.builder()
				.id(1)
				.word("court")
				.language(Language.AZ)
				.build();
		CreateScrapeJobRequestDTO request = new CreateScrapeJobRequestDTO(
				source.getId(),
				keyword.getId(),
				LocalDate.of(2026, 7, 8),
				LocalDate.of(2026, 7, 8),
				null,
				null
		);
		when(sourceService.getEntity(source.getId())).thenReturn(source);
		when(keywordService.getEntity(keyword.getId())).thenReturn(keyword);

		assertThatThrownBy(() -> scrapeJobService.create(request))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("does not support keyword language");
		verify(scrapeJobRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
