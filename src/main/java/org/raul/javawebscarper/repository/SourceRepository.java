package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceRepository extends JpaRepository<Source, Integer> {
}
