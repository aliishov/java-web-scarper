package org.raul.javawebscarper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "authors", schema = "core")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
public class Author {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	UUID id;

	@Column(name = "username", nullable = false)
	String username;

	@Column(name = "source_id", nullable = false)
	Integer sourceId;

	@Column(name = "external_id")
	Integer externalId;

	@Column(name = "profile_url", nullable = false, unique = true, columnDefinition = "TEXT")
	String profileUrl;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	OffsetDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updatedAt")
	OffsetDateTime updatedAt;
}
