package org.raul.javawebscarper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "post_keywords", schema = "core")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@SequenceGenerator(name = "pkw_seq", sequenceName = "core.post_keywords_seq", allocationSize = 50)
public class PostKeyword {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pkw_seq")
	Integer id;

	@Column(name = "post_id", nullable = false, updatable = false)
	UUID postId;

	@Column(name = "keyword_id", nullable = false, updatable = false)
	Integer keywordId;

	@Column(name = "matched_text", nullable = false, updatable = false)
	String matchedText;
}
