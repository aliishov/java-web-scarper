INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'LENT_AZ', 'Lent.az', 'NEWS', 'https://lent.az/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) = lower('LENT_AZ')
		OR source.base_url = 'https://lent.az/'
		OR source.base_url = 'https://lent.az'
);

UPDATE core.sources
SET code = 'LENT_AZ',
	name = 'Lent.az',
	type = 'NEWS',
	base_url = 'https://lent.az/',
	updated_at = now()
WHERE lower(code) = lower('LENT_AZ')
	OR base_url = 'https://lent.az/'
	OR base_url = 'https://lent.az';

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, 'AZ'
FROM core.sources source
WHERE lower(source.code) = lower('LENT_AZ')
	OR source.base_url = 'https://lent.az/'
	OR source.base_url = 'https://lent.az'
ON CONFLICT DO NOTHING;

INSERT INTO core.authors (id, source_id, username, external_id, profile_url, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000008', source.id, 'lent.az', 'lent.az', 'https://lent.az/', now(), now()
FROM core.sources source
WHERE lower(source.code) = lower('LENT_AZ')
	OR source.base_url = 'https://lent.az/'
	OR source.base_url = 'https://lent.az'
ON CONFLICT (source_id, username) DO NOTHING;
