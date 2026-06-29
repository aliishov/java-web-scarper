package org.raul.javawebscarper.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import org.raul.javawebscarper.model.enumerated.MediaType;

@Entity
@Table(
		name = "post_medias",
		schema = "core",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_post_medias_post_media_url", columnNames = {"post_id", "media_url"}),
				@UniqueConstraint(name = "uq_post_medias_post_position", columnNames = {"post_id", "position"})
		},
		indexes = {
				@Index(name = "idx_post_medias_post_id", columnList = "post_id")
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
@SequenceGenerator(name = "pm_seq", sequenceName = "core.post_medias_seq", allocationSize = 50)
public class PostMedia extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pm_seq")
	Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false, updatable = false)
	Post post;

	@Enumerated(EnumType.STRING)
	@Column(name = "media_type", nullable = false, updatable = false)
	MediaType mediaType;

	@Column(name = "media_url", nullable = false, updatable = false, columnDefinition = "TEXT")
	String mediaUrl;

	@Column(name = "position", nullable = false)
	Integer position;
}
