package org.raul.javawebscarper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.raul.javawebscarper.model.enumerated.AdminRole;

@Entity
@Table(name = "admins", schema = "core")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@SequenceGenerator(name = "admin_seq", sequenceName = "core.admins_seq", allocationSize = 1)
public class Admin extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "admin_seq")
	Long id;

	@Column(nullable = false, unique = true, length = 100)
	String username;

	@Column(name = "password_hash", nullable = false)
	String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	@Builder.Default
	AdminRole role = AdminRole.ADMIN;
}
