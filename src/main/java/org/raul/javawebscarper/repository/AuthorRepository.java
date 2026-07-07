package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Source;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthorRepository extends JpaRepository<Author, UUID> {

	Page<Author> findBySource(Source source, Pageable pageable);

	Optional<Author> findBySourceAndExternalId(Source source, String externalId);

	Optional<Author> findBySourceAndUsername(Source source, String username);

	Optional<Author> findBySourceAndProfileUrl(Source source, String profileUrl);

	Optional<Author> findByProfileUrl(String profileUrl);

	boolean existsBySourceAndExternalId(Source source, String externalId);

	boolean existsBySourceAndExternalIdAndIdNot(Source source, String externalId, UUID id);

	boolean existsBySourceAndUsername(Source source, String username);

	boolean existsBySourceAndUsernameAndIdNot(Source source, String username, UUID id);

	boolean existsByProfileUrl(String profileUrl);

	boolean existsByProfileUrlAndIdNot(String profileUrl, UUID id);

	boolean existsBySource(Source source);
}
