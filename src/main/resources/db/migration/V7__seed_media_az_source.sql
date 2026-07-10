INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'MEDIA_AZ', 'Media.az', 'NEWS', 'https://media.az/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) = lower('MEDIA_AZ')
		OR source.base_url = 'https://media.az/'
		OR source.base_url = 'https://media.az'
);

UPDATE core.sources
SET code = 'MEDIA_AZ',
	name = 'Media.az',
	type = 'NEWS',
	base_url = 'https://media.az/',
	updated_at = now()
WHERE lower(code) = lower('MEDIA_AZ')
	OR base_url = 'https://media.az/'
	OR base_url = 'https://media.az';

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, 'RU'
FROM core.sources source
WHERE lower(source.code) = lower('MEDIA_AZ')
	OR source.base_url = 'https://media.az/'
	OR source.base_url = 'https://media.az'
ON CONFLICT DO NOTHING;

INSERT INTO core.authors (id, source_id, username, external_id, profile_url, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000003', source.id, 'media.az', 'media.az', 'https://media.az/', now(), now()
FROM core.sources source
WHERE lower(source.code) = lower('MEDIA_AZ')
	OR source.base_url = 'https://media.az/'
	OR source.base_url = 'https://media.az'
ON CONFLICT (source_id, username) DO NOTHING;
