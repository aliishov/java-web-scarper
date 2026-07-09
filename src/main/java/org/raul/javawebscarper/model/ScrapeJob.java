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
import org.raul.javawebscarper.model.enumerated.ScrapeJobRunType;
import org.raul.javawebscarper.model.enumerated.ScrapeJobStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
		name = "scrape_jobs",
		schema = "core",
		indexes = {
				@Index(name = "idx_scrape_jobs_source_status", columnList = "source_id,status"),
				@Index(name = "idx_scrape_jobs_keyword_status", columnList = "keyword_id,status"),
				@Index(name = "idx_scrape_jobs_created_at", columnList = "created_at"),
				@Index(name = "idx_scrape_jobs_started_at", columnList = "started_at"),
				@Index(
						name = "idx_scrape_jobs_source_keyword_dates_run_type",
						columnList = "source_id,keyword_id,date_from,date_to,run_type"
				)
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
public class ScrapeJob extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_id", nullable = false)
	Source source;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "keyword_id", nullable = false)
	Keyword keyword;

	@Column(name = "date_from", nullable = false)
	LocalDate dateFrom;

	@Column(name = "date_to", nullable = false)
	LocalDate dateTo;

	@Enumerated(EnumType.STRING)
	@Column(name = "run_type", nullable = false, length = 50)
	@Builder.Default
	ScrapeJobRunType runType = ScrapeJobRunType.SCHEDULED;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	@Builder.Default
	ScrapeJobStatus status = ScrapeJobStatus.PENDING;

	@Column(name = "started_at")
	OffsetDateTime startedAt;

	@Column(name = "finished_at")
	OffsetDateTime finishedAt;

	@Column(name = "posts_found", nullable = false)
	@Builder.Default
	int postsFound = 0;

	@Column(name = "posts_saved", nullable = false)
	@Builder.Default
	int postsSaved = 0;

	@Column(name = "error_message", columnDefinition = "TEXT")
	String errorMessage;
}
