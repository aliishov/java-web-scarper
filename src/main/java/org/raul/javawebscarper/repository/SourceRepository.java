package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceRepository extends JpaRepository<Source, Integer> {

	Optional<Source> findByCode(String code);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, Integer id);

	List<Source> findByEnabledTrue();
}
