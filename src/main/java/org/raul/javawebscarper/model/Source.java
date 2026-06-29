package org.raul.javawebscarper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
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

import org.raul.javawebscarper.model.enumerated.SourceType;

@Entity
@Table(
		name = "sources",
		schema = "core",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_sources_code", columnNames = "code"),
				@UniqueConstraint(name = "uq_sources_name", columnNames = "name"),
				@UniqueConstraint(name = "uq_sources_base_url", columnNames = "base_url")
		},
		indexes = {
				@Index(name = "idx_sources_type_enabled", columnList = "type,is_enabled")
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
@SequenceGenerator(name = "source_seq", sequenceName = "core.sources_seq", allocationSize = 50)
public class Source extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "source_seq")
	Integer id;

	@Column(name = "code", nullable = false, length = 100)
	String code;

	@Column(name = "name", nullable = false)
	String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 32)
	SourceType type;

	@Column(name = "base_url", nullable = false, columnDefinition = "TEXT")
	String baseUrl;

	@Column(name = "is_enabled", nullable = false)
	@Builder.Default
	boolean enabled = true;
}
