package org.raul.javawebscarper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

@Entity
@Table(
		name = "authors",
		schema = "core",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_authors_source_external_id", columnNames = {"source_id", "external_id"}),
				@UniqueConstraint(name = "uq_authors_profile_url", columnNames = "profile_url")
		},
		indexes = {
				@Index(name = "idx_authors_source_id", columnList = "source_id"),
				@Index(name = "idx_authors_source_username", columnList = "source_id,username")
		}
)
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
public class Author extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_id", nullable = false)
	Source source;

	@Column(name = "username", nullable = false)
	String username;

	@Column(name = "external_id")
	String externalId;

	@Column(name = "profile_url", columnDefinition = "TEXT")
	String profileUrl;
}
