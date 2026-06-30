package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.PostKeyword;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostKeywordRepository extends JpaRepository<PostKeyword, Integer> {

	Optional<PostKeyword> findByPostAndKeyword(Post post, Keyword keyword);

	boolean existsByPostAndKeyword(Post post, Keyword keyword);

	List<PostKeyword> findByKeyword(Keyword keyword);

	boolean existsByKeyword(Keyword keyword);
}
