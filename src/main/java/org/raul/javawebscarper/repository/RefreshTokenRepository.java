package org.raul.javawebscarper.repository;

import org.raul.javawebscarper.model.RefreshToken;
import org.raul.javawebscarper.model.Admin;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from RefreshToken token join fetch token.admin where token.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	void deleteByAdmin(Admin admin);
}
