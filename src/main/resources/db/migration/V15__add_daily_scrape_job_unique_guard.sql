CREATE UNIQUE INDEX uq_scrape_jobs_daily_previous_day
	ON core.scrape_jobs (source_id, keyword_id, date_from, date_to, run_type)
	WHERE run_type = 'DAILY_PREVIOUS_DAY';
