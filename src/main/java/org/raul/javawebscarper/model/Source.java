package org.raul.javawebscarper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import org.raul.javawebscarper.model.enumerated.SourceType;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "sources", schema = "core")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@SequenceGenerator(name = "sr_seq", sequenceName = "core.sources_seq", allocationSize = 50)
public class Source {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sr_seq")
	Integer id;

	@Column(name = "code", nullable = false, unique = true)
	Integer code;

	@Column(name = "name", nullable = false, unique = true)
	String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false)
	SourceType type;

	@Column(name = "base_url", nullable = false, unique = true)
	String baseUrl;

	@Column(name = "is_enabled", nullable = false)
	boolean enabled = true;
}
