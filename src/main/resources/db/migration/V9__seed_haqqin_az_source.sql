INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'HAQQIN_AZ', 'Haqqin.az', 'NEWS', 'https://haqqin.az/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) = lower('HAQQIN_AZ')
		OR source.base_url = 'https://haqqin.az/'
		OR source.base_url = 'https://haqqin.az'
);

UPDATE core.sources
SET code = 'HAQQIN_AZ',
	name = 'Haqqin.az',
	type = 'NEWS',
	base_url = 'https://haqqin.az/',
	updated_at = now()
WHERE lower(code) = lower('HAQQIN_AZ')
	OR base_url = 'https://haqqin.az/'
	OR base_url = 'https://haqqin.az';

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, 'RU'
FROM core.sources source
WHERE lower(source.code) = lower('HAQQIN_AZ')
	OR source.base_url = 'https://haqqin.az/'
	OR source.base_url = 'https://haqqin.az'
ON CONFLICT DO NOTHING;

INSERT INTO core.authors (id, source_id, username, external_id, profile_url, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000005', source.id, 'haqqin.az', 'haqqin.az', 'https://haqqin.az/', now(), now()
FROM core.sources source
WHERE lower(source.code) = lower('HAQQIN_AZ')
	OR source.base_url = 'https://haqqin.az/'
	OR source.base_url = 'https://haqqin.az'
ON CONFLICT (source_id, username) DO NOTHING;
