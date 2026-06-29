package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthorRepository extends JpaRepository<Author, UUID> {

	Optional<Author> findBySourceAndExternalId(Source source, String externalId);

	Optional<Author> findBySourceAndUsername(Source source, String username);

	Optional<Author> findByProfileUrl(String profileUrl);
}
