ALTER TABLE core.scrape_jobs
	ADD COLUMN run_type varchar(50);

UPDATE core.scrape_jobs
SET run_type = 'SCHEDULED'
WHERE run_type IS NULL;

ALTER TABLE core.scrape_jobs
	ALTER COLUMN run_type SET NOT NULL;

CREATE INDEX idx_scrape_jobs_source_keyword_dates_run_type
	ON core.scrape_jobs (source_id, keyword_id, date_from, date_to, run_type);
