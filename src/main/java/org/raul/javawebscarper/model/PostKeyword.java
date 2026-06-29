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

@Entity
@Table(
		name = "post_keywords",
		schema = "core",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_post_keywords_post_keyword", columnNames = {"post_id", "keyword_id"})
		},
		indexes = {
				@Index(name = "idx_post_keywords_post_id", columnList = "post_id"),
				@Index(name = "idx_post_keywords_keyword_id", columnList = "keyword_id")
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
@SequenceGenerator(name = "pkw_seq", sequenceName = "core.post_keywords_seq", allocationSize = 50)
public class PostKeyword extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pkw_seq")
	Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false, updatable = false)
	Post post;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "keyword_id", nullable = false, updatable = false)
	Keyword keyword;

	@Column(name = "matched_text", updatable = false, columnDefinition = "TEXT")
	String matchedText;
}
