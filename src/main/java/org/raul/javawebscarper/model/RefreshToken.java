package org.raul.javawebscarper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

@Entity
@Table(name = "refresh_tokens", schema = "core")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@SequenceGenerator(name = "refresh_token_seq", sequenceName = "core.refresh_tokens_seq", allocationSize = 50)
public class RefreshToken extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refresh_token_seq")
	Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "admin_id", nullable = false)
	Admin admin;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	String tokenHash;

	@Column(name = "expires_at", nullable = false)
	OffsetDateTime expiresAt;

	@Column(name = "revoked_at")
	OffsetDateTime revokedAt;
}
