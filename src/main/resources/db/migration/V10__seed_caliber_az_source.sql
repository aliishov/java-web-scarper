INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'CALIBER_AZ', 'Caliber.az', 'NEWS', 'https://caliber.az/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) = lower('CALIBER_AZ')
		OR source.base_url = 'https://caliber.az/'
		OR source.base_url = 'https://caliber.az'
);

UPDATE core.sources
SET code = 'CALIBER_AZ',
	name = 'Caliber.az',
	type = 'NEWS',
	base_url = 'https://caliber.az/',
	updated_at = now()
WHERE lower(code) = lower('CALIBER_AZ')
	OR base_url = 'https://caliber.az/'
	OR base_url = 'https://caliber.az';

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, 'RU'
FROM core.sources source
WHERE lower(source.code) = lower('CALIBER_AZ')
	OR source.base_url = 'https://caliber.az/'
	OR source.base_url = 'https://caliber.az'
ON CONFLICT DO NOTHING;

INSERT INTO core.authors (id, source_id, username, external_id, profile_url, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000006', source.id, 'caliber.az', 'caliber.az', 'https://caliber.az/', now(), now()
FROM core.sources source
WHERE lower(source.code) = lower('CALIBER_AZ')
	OR source.base_url = 'https://caliber.az/'
	OR source.base_url = 'https://caliber.az'
ON CONFLICT (source_id, username) DO NOTHING;
