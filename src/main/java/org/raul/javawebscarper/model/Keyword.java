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

@Entity
@Table(name = "keywords", schema = "core")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@SequenceGenerator(name = "kw_seq", sequenceName = "core.keywords_seq", allocationSize = 50)
public class Keyword {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "kw_seq")
	Integer id;

	@Column(name = "word", nullable = false, unique = true, updatable = false)
	String word;

	@Column(name = "is_enabled", nullable = false)
	boolean enabled = true;
}
