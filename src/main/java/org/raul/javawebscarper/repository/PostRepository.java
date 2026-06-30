package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.Source;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID>, JpaSpecificationExecutor<Post> {

	Optional<Post> findByPostUrl(String postUrl);

	Optional<Post> findBySourceAndExternalPostId(Source source, String externalPostId);

	boolean existsByPostUrl(String postUrl);

	boolean existsBySourceAndExternalPostId(Source source, String externalPostId);

	boolean existsBySourceAndExternalPostIdAndIdNot(Source source, String externalPostId, UUID id);

	boolean existsBySourceAndPostUrl(Source source, String postUrl);

	boolean existsBySourceAndPostUrlAndIdNot(Source source, String postUrl, UUID id);

	boolean existsBySourceAndTextHash(Source source, String textHash);

	boolean existsBySourceAndTextHashAndIdNot(Source source, String textHash, UUID id);

	boolean existsBySource(Source source);

	boolean existsByAuthor(Author author);

	Page<Post> findBySource(Source source, Pageable pageable);

	Page<Post> findByAuthor(Author author, Pageable pageable);

	@Query(
			value = "select distinct p from Post p join p.keywords pk where pk.keyword = :keyword",
			countQuery = "select count(distinct p) from Post p join p.keywords pk where pk.keyword = :keyword"
	)
	Page<Post> findByKeyword(@Param("keyword") Keyword keyword, Pageable pageable);
}
