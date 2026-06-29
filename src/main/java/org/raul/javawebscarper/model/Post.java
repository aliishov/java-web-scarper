package org.raul.javawebscarper.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
		name = "posts",
		schema = "core",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_posts_post_url", columnNames = "post_url"),
				@UniqueConstraint(name = "uq_posts_source_external_post_id", columnNames = {"source_id", "external_post_id"})
		},
		indexes = {
				@Index(name = "idx_posts_source_post_date", columnList = "source_id,post_date"),
				@Index(name = "idx_posts_author_post_date", columnList = "author_id,post_date"),
				@Index(name = "idx_posts_text_hash", columnList = "text_hash"),
				@Index(name = "idx_posts_scraped_at", columnList = "scraped_at")
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
public class Post extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_id", nullable = false, updatable = false)
	Source source;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false, updatable = false)
	Author author;

	@Column(name = "external_post_id", updatable = false)
	String externalPostId;

	@Column(name = "post_url", nullable = false, updatable = false, columnDefinition = "TEXT")
	String postUrl;

	@Column(name = "post_date", nullable = false, updatable = false)
	OffsetDateTime postDate;

	@Column(name = "scraped_at", nullable = false, updatable = false)
	OffsetDateTime scrapedAt;

	@Column(name = "text", nullable = false, updatable = false, columnDefinition = "TEXT")
	String text;

	@Column(name = "text_hash", nullable = false, updatable = false, length = 128)
	String textHash;

	@Column(name = "language", length = 16)
	String language;

	@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	@Builder.Default
	List<PostMedia> media = new ArrayList<>();

	@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	Set<PostKeyword> keywords = new LinkedHashSet<>();

	public void addMedia(PostMedia postMedia) {
		media.add(postMedia);
		postMedia.setPost(this);
	}

	public void removeMedia(PostMedia postMedia) {
		media.remove(postMedia);
		postMedia.setPost(null);
	}

	public void addKeyword(Keyword keyword, String matchedText) {
		PostKeyword postKeyword = PostKeyword.builder()
				.post(this)
				.keyword(keyword)
				.matchedText(matchedText)
				.build();
		keywords.add(postKeyword);
	}
}
