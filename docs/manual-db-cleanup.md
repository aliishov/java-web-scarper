# Manual DB Cleanup For Scraper QA

Use this only for local/manual scraper QA when runtime data must be reset without changing Flyway migrations or database structure.

This cleanup keeps:

- `core.flyway_schema_history`
- `core.sources`
- `core.keywords`

It clears only runtime scraping data:

- `core.post_medias`
- `core.post_keywords`
- `core.posts`
- `core.scrape_jobs`
- `core.authors`

Run through Docker Compose:

```powershell
docker compose exec -T postgres psql -U postgres -d scarper_db -c "TRUNCATE TABLE core.post_medias, core.post_keywords, core.posts, core.scrape_jobs, core.authors RESTART IDENTITY CASCADE;"
```

Verify runtime tables are empty:

```powershell
docker compose exec -T postgres psql -U postgres -d scarper_db -c "SELECT 'posts' AS table_name, COUNT(*) FROM core.posts UNION ALL SELECT 'post_medias', COUNT(*) FROM core.post_medias UNION ALL SELECT 'post_keywords', COUNT(*) FROM core.post_keywords UNION ALL SELECT 'scrape_jobs', COUNT(*) FROM core.scrape_jobs UNION ALL SELECT 'authors', COUNT(*) FROM core.authors;"
```

Do not add this cleanup as a migration or application startup hook.
