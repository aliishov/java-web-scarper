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
import org.raul.javawebscarper.model.enumerated.Language;

@Entity
@Table(
		name = "keywords",
		schema = "core",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_keywords_word", columnNames = "word")
		},
		indexes = {
				@Index(name = "idx_keywords_enabled", columnList = "is_enabled"),
				@Index(name = "idx_keywords_language_enabled", columnList = "language,is_enabled")
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
@SequenceGenerator(name = "kw_seq", sequenceName = "core.keywords_seq", allocationSize = 50)
public class Keyword extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "kw_seq")
	Integer id;

	@Column(name = "word", nullable = false)
	String word;

	@Enumerated(EnumType.STRING)
	@Column(name = "language", nullable = false, length = 10)
	@Builder.Default
	Language language = Language.AZ;

	@Column(name = "is_enabled", nullable = false)
	@Builder.Default
	boolean enabled = true;
}
