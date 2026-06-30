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
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.raul.javawebscarper.repository.ScrapeJobRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScrapeJobService {

	private final ScrapeJobRepository scrapeJobRepository;
	private final SourceService sourceService;
	private final KeywordService keywordService;
	private final ScrapeJobMapper scrapeJobMapper;

	@Transactional
	public ScrapeJobResponseDTO create(CreateScrapeJobRequestDTO request) {
		validateDateRange(request.dateFrom(), request.dateTo());
		Source source = sourceService.getEntity(request.sourceId());
		Keyword keyword = keywordService.getEntity(request.keywordId());
		ScrapeJob scrapeJob = scrapeJobMapper.toEntity(request, source, keyword);
		return scrapeJobMapper.toResponse(scrapeJobRepository.save(scrapeJob));
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
		return PageResponseDTO.from(scrapeJobRepository.findAll(specification, pageable), scrapeJobMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public ScrapeJobResponseDTO findById(UUID id) {
		return scrapeJobMapper.toResponse(getEntity(id));
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<ScrapeJobResponseDTO> findBySource(Integer sourceId, Pageable pageable) {
		Source source = sourceService.getEntity(sourceId);
		return PageResponseDTO.from(scrapeJobRepository.findBySource(source, pageable), scrapeJobMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public PageResponseDTO<ScrapeJobResponseDTO> findByStatus(ScrapeJobStatus status, Pageable pageable) {
		return PageResponseDTO.from(scrapeJobRepository.findByStatus(status, pageable), scrapeJobMapper::toResponse);
	}

	@Transactional
	public ScrapeJobResponseDTO start(UUID id) {
		ScrapeJob scrapeJob = getEntity(id);
		if (scrapeJob.getStatus() != ScrapeJobStatus.PENDING) {
			throw new BadRequestException("Only PENDING scrape jobs can be started");
		}
		scrapeJob.setStatus(ScrapeJobStatus.RUNNING);
		scrapeJob.setStartedAt(OffsetDateTime.now());
		scrapeJob.setFinishedAt(null);
		scrapeJob.setErrorMessage(null);
		return scrapeJobMapper.toResponse(scrapeJob);
	}

	@Transactional
	public ScrapeJobResponseDTO complete(UUID id, CompleteScrapeJobRequestDTO request) {
		ScrapeJob scrapeJob = getEntity(id);
		if (scrapeJob.getStatus() != ScrapeJobStatus.RUNNING) {
			throw new BadRequestException("Only RUNNING scrape jobs can be completed");
		}
		if (request.postsSaved() > request.postsFound()) {
			throw new BadRequestException("postsSaved must not be greater than postsFound");
		}
		scrapeJob.setStatus(ScrapeJobStatus.SUCCESS);
		scrapeJob.setFinishedAt(OffsetDateTime.now());
		scrapeJob.setPostsFound(request.postsFound());
		scrapeJob.setPostsSaved(request.postsSaved());
		scrapeJob.setErrorMessage(null);
		return scrapeJobMapper.toResponse(scrapeJob);
	}

	@Transactional
	public ScrapeJobResponseDTO fail(UUID id, FailScrapeJobRequestDTO request) {
		ScrapeJob scrapeJob = getEntity(id);
		if (scrapeJob.getStatus() != ScrapeJobStatus.PENDING && scrapeJob.getStatus() != ScrapeJobStatus.RUNNING) {
			throw new BadRequestException("Only PENDING or RUNNING scrape jobs can be failed");
		}
		scrapeJob.setStatus(ScrapeJobStatus.FAILED);
		scrapeJob.setFinishedAt(OffsetDateTime.now());
		scrapeJob.setErrorMessage(request.errorMessage().trim());
		return scrapeJobMapper.toResponse(scrapeJob);
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
}
