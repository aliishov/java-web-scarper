package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Keyword;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Integer> {

	Optional<Keyword> findByWord(String word);

	boolean existsByWord(String word);

	List<Keyword> findByEnabledTrue();
}
