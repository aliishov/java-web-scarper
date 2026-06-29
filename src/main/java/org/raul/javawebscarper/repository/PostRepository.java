package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.Source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

	Optional<Post> findByPostUrl(String postUrl);

	Optional<Post> findBySourceAndExternalPostId(Source source, String externalPostId);

	boolean existsByPostUrl(String postUrl);

	List<Post> findBySourceAndPostDateBetween(Source source, OffsetDateTime dateFrom, OffsetDateTime dateTo);

	@Query("select distinct p from Post p join p.keywords pk where pk.keyword = :keyword")
	List<Post> findByKeyword(@Param("keyword") Keyword keyword);
}
