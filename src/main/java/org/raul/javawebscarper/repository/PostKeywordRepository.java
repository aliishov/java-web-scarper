package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.PostKeyword;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostKeywordRepository extends JpaRepository<PostKeyword, Integer> {
}
