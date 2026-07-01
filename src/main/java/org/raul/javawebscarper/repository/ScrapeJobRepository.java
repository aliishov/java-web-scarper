package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScrapeJobRepository extends JpaRepository<ScrapeJob, UUID>, JpaSpecificationExecutor<ScrapeJob> {

	List<ScrapeJob> findBySourceAndStatus(Source source, ScrapeJobStatus status);

	List<ScrapeJob> findByKeywordAndStatus(Keyword keyword, ScrapeJobStatus status);

	List<ScrapeJob> findTop20ByOrderByCreatedAtDesc();

	Page<ScrapeJob> findBySource(Source source, Pageable pageable);

	Page<ScrapeJob> findByStatus(ScrapeJobStatus status, Pageable pageable);

	boolean existsBySource(Source source);

	boolean existsByKeyword(Keyword keyword);

	boolean existsBySourceAndKeywordAndDateFromAndDateToAndStatusIn(
			Source source,
			Keyword keyword,
			LocalDate dateFrom,
			LocalDate dateTo,
			Collection<ScrapeJobStatus> statuses
	);

	@EntityGraph(attributePaths = {"source", "keyword"})
	@Query("select scrapeJob from ScrapeJob scrapeJob where scrapeJob.id = :id")
	Optional<ScrapeJob> findByIdWithSourceAndKeyword(@Param("id") UUID id);
}
