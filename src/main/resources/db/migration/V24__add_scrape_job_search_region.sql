ALTER TABLE core.scrape_jobs
	ADD COLUMN search_region varchar(16) NOT NULL DEFAULT 'AZ';

DROP INDEX IF EXISTS core.uq_scrape_jobs_daily_previous_day;

CREATE UNIQUE INDEX uq_scrape_jobs_daily_previous_day
	ON core.scrape_jobs (source_id, keyword_id, date_from, date_to, search_region, run_type)
	WHERE run_type = 'DAILY_PREVIOUS_DAY';

DROP INDEX IF EXISTS core.idx_scrape_jobs_source_keyword_dates_run_type;

CREATE INDEX idx_scrape_jobs_source_keyword_dates_run_type
	ON core.scrape_jobs (source_id, keyword_id, date_from, date_to, search_region, run_type);
