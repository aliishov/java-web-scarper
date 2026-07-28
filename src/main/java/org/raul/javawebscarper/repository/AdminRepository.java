package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.raul.javawebscarper.model.enumerated.AdminRole;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

	Optional<Admin> findByUsernameIgnoreCase(String username);

	boolean existsByUsernameIgnoreCase(String username);

	boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

	long countByRole(AdminRole role);
}
