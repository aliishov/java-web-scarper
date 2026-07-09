package org.raul.javawebscarper.service;

import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.dto.request.scrapejob.CompleteScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.request.scrapejob.FailScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.request.scrapejob.CreateScrapeJobRequestDTO;
import org.raul.javawebscarper.dto.response.scrapejob.ScrapeJobResponseDTO;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.exception.ResourceNotFoundException;
import org.raul.javawebscarper.mapper.ScrapeJobMapper;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.raul.javawebscarper.repository.ScrapeJobRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScrapeJobService {

	private static final List<ScrapeJobStatus> ACTIVE_JOB_STATUSES = List.of(
			ScrapeJobStatus.PENDING,
			ScrapeJobStatus.RUNNING
	);
	private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;

	private final ScrapeJobRepository scrapeJobRepository;
	private final SourceService sourceService;
	private final KeywordService keywordService;

	@Transactional
	public ScrapeJobResponseDTO create(CreateScrapeJobRequestDTO request) {
		validateDateRange(request.dateFrom(), request.dateTo());
		Source source = sourceService.getEntity(request.sourceId());
		Keyword keyword = keywordService.getEntity(request.keywordId());
		ScrapeJob scrapeJob = ScrapeJobMapper.toEntity(request, source, keyword);
		return ScrapeJobMapper.toResponse(scrapeJobRepository.save(scrapeJob));
	}

	@Transactional
	public ScrapeJob createPendingJob(Source source, Keyword keyword, LocalDate dateFrom, LocalDate dateTo) {
		return createPendingJob(source, keyword, dateFrom, dateTo, ScrapeJobRunType.SCHEDULED);
	}

	@Transactional
	public ScrapeJob createPendingJob(
			Source source,
			Keyword keyword,
			LocalDate dateFrom,
			LocalDate dateTo,
			ScrapeJobRunType runType
	) {
		validateDateRange(dateFrom, dateTo);
		ScrapeJob scrapeJob = ScrapeJob.builder()
				.source(source)
				.keyword(keyword)
				.dateFrom(dateFrom)
				.dateTo(dateTo)
				.runType(runType == null ? ScrapeJobRunType.SCHEDULED : runType)
				.status(ScrapeJobStatus.PENDING)
				.postsFound(0)
				.postsSaved(0)
				.build();
		return scrapeJobRepository.save(scrapeJob);
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<ScrapeJobResponseDTO> findAll(
			Integer sourceId,
			Integer keywordId,
			ScrapeJobStatus status,
			LocalDate dateFrom,
			LocalDate dateTo,
			Pageable pageable
	) {
		validateDateRange(dateFrom, dateTo);
		Specification<ScrapeJob> specification = buildSpecification(sourceId, keywordId, status, dateFrom, dateTo);
		return PageResponseDTO.from(scrapeJobRepository.findAll(specification, pageable), ScrapeJobMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public ScrapeJobResponseDTO findById(UUID id) {
		return ScrapeJobMapper.toResponse(getEntity(id));
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<ScrapeJobResponseDTO> findBySource(Integer sourceId, Pageable pageable) {
		Source source = sourceService.getEntity(sourceId);
		return PageResponseDTO.from(scrapeJobRepository.findBySource(source, pageable), ScrapeJobMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<ScrapeJobResponseDTO> findByStatus(ScrapeJobStatus status, Pageable pageable) {
		return PageResponseDTO.from(scrapeJobRepository.findByStatus(status, pageable), ScrapeJobMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public boolean hasActiveJob(Source source, Keyword keyword, LocalDate dateFrom, LocalDate dateTo) {
		return scrapeJobRepository.existsBySourceAndKeywordAndDateFromAndDateToAndStatusIn(
				source,
				keyword,
				dateFrom,
				dateTo,
				ACTIVE_JOB_STATUSES
		);
	}

	@Transactional(readOnly = true)
	public boolean hasJobForRunType(
			Source source,
			Keyword keyword,
			LocalDate dateFrom,
			LocalDate dateTo,
			ScrapeJobRunType runType
	) {
		return scrapeJobRepository.existsBySourceAndKeywordAndDateFromAndDateToAndRunType(
				source,
				keyword,
				dateFrom,
				dateTo,
				runType
		);
	}

	@Transactional
	public ScrapeJobResponseDTO start(UUID id) {
		return ScrapeJobMapper.toResponse(markRunning(id));
	}

	@Transactional
	public ScrapeJob markRunning(UUID id) {
		ScrapeJob scrapeJob = getEntityWithSourceAndKeyword(id);
		if (scrapeJob.getStatus() != ScrapeJobStatus.PENDING) {
			throw new BadRequestException("Only PENDING scrape jobs can be started");
		}
		scrapeJob.setStatus(ScrapeJobStatus.RUNNING);
		scrapeJob.setStartedAt(OffsetDateTime.now());
		scrapeJob.setFinishedAt(null);
		scrapeJob.setErrorMessage(null);
		return scrapeJob;
	}

	@Transactional
	public ScrapeJobResponseDTO complete(UUID id, CompleteScrapeJobRequestDTO request) {
		return ScrapeJobMapper.toResponse(markSuccess(id, request.postsFound(), request.postsSaved()));
	}

	@Transactional
	public ScrapeJob markSuccess(UUID id, int postsFound, int postsSaved) {
		ScrapeJob scrapeJob = getEntity(id);
		if (scrapeJob.getStatus() != ScrapeJobStatus.RUNNING) {
			throw new BadRequestException("Only RUNNING scrape jobs can be completed");
		}
		if (postsFound < 0 || postsSaved < 0) {
			throw new BadRequestException("postsFound and postsSaved must not be negative");
		}
		if (postsSaved > postsFound) {
			throw new BadRequestException("postsSaved must not be greater than postsFound");
		}
		scrapeJob.setStatus(ScrapeJobStatus.SUCCESS);
		scrapeJob.setFinishedAt(OffsetDateTime.now());
		scrapeJob.setPostsFound(postsFound);
		scrapeJob.setPostsSaved(postsSaved);
		scrapeJob.setErrorMessage(null);
		return scrapeJob;
	}

	@Transactional
	public ScrapeJobResponseDTO fail(UUID id, FailScrapeJobRequestDTO request) {
		return ScrapeJobMapper.toResponse(markFailed(id, request.errorMessage()));
	}

	@Transactional
	public ScrapeJob markFailed(UUID id, String errorMessage) {
		ScrapeJob scrapeJob = getEntity(id);
		if (scrapeJob.getStatus() != ScrapeJobStatus.PENDING && scrapeJob.getStatus() != ScrapeJobStatus.RUNNING) {
			throw new BadRequestException("Only PENDING or RUNNING scrape jobs can be failed");
		}
		scrapeJob.setStatus(ScrapeJobStatus.FAILED);
		scrapeJob.setFinishedAt(OffsetDateTime.now());
		scrapeJob.setErrorMessage(normalizeErrorMessage(errorMessage));
		return scrapeJob;
	}

	@Transactional
	public void delete(UUID id) {
		ScrapeJob scrapeJob = getEntity(id);
		if (scrapeJob.getStatus() == ScrapeJobStatus.RUNNING) {
			throw new BadRequestException("Running scrape jobs cannot be deleted");
		}
		scrapeJobRepository.delete(scrapeJob);
	}

	@Transactional(readOnly = true)
	public ScrapeJob getEntity(UUID id) {
		return scrapeJobRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("ScrapeJob with id '%s' was not found".formatted(id)));
	}

	@Transactional(readOnly = true)
	public ScrapeJob getEntityWithSourceAndKeyword(UUID id) {
		return scrapeJobRepository.findByIdWithSourceAndKeyword(id)
				.orElseThrow(() -> new ResourceNotFoundException("ScrapeJob with id '%s' was not found".formatted(id)));
	}

	private Specification<ScrapeJob> buildSpecification(
			Integer sourceId,
			Integer keywordId,
			ScrapeJobStatus status,
			LocalDate dateFrom,
			LocalDate dateTo
	) {
		Specification<ScrapeJob> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

		if (sourceId != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.equal(root.get("source").get("id"), sourceId));
		}
		if (keywordId != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.equal(root.get("keyword").get("id"), keywordId));
		}
		if (status != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.equal(root.get("status"), status));
		}
		if (dateFrom != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.greaterThanOrEqualTo(root.get("dateFrom"), dateFrom));
		}
		if (dateTo != null) {
			specification = specification.and((root, query, criteriaBuilder) ->
					criteriaBuilder.lessThanOrEqualTo(root.get("dateTo"), dateTo));
		}
		return specification;
	}

	private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
		if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
			throw new BadRequestException("dateTo must be greater than or equal to dateFrom");
		}
	}

	private String normalizeErrorMessage(String errorMessage) {
		if (errorMessage == null || errorMessage.isBlank()) {
			return "Unknown scrape job error";
		}
		String trimmed = errorMessage.trim();
		if (trimmed.length() <= MAX_ERROR_MESSAGE_LENGTH) {
			return trimmed;
		}
		return trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH);
	}
}
