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

import org.raul.javawebscarper.model.enumerated.MediaType;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "post_medias", schema = "core")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@SequenceGenerator(name = "pm_seq", sequenceName = "core.post_medias_seq", allocationSize = 50)
public class PostMedia {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sr_seq")
	Long id;

	@Column(name = "post_id", nullable = false, updatable = false)
	UUID postId;

	@Enumerated(EnumType.STRING)
	@Column(name = "media_type", nullable = false, updatable = false)
	MediaType mediaType;

	@Column(name = "media_url", nullable = false, updatable = false, unique = true, columnDefinition = "TEXT")
	String mediaUrl;
}
