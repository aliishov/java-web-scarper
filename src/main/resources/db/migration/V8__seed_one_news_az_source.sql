INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'ONE_NEWS_AZ', '1news.az', 'NEWS', 'https://1news.az/az', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) = lower('ONE_NEWS_AZ')
		OR source.base_url = 'https://1news.az/az'
		OR source.base_url = 'https://1news.az/az/'
);

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, 'AZ'
FROM core.sources source
WHERE lower(source.code) = lower('ONE_NEWS_AZ')
	OR source.base_url = 'https://1news.az/az'
	OR source.base_url = 'https://1news.az/az/'
ON CONFLICT DO NOTHING;

INSERT INTO core.authors (id, source_id, username, external_id, profile_url, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000004', source.id, '1news.az', '1news.az', 'https://1news.az/az', now(), now()
FROM core.sources source
WHERE lower(source.code) = lower('ONE_NEWS_AZ')
	OR source.base_url = 'https://1news.az/az'
	OR source.base_url = 'https://1news.az/az/'
ON CONFLICT (source_id, username) DO NOTHING;
