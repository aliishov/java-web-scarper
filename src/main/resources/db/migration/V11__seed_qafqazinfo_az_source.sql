INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'QAFQAZINFO_AZ', 'Qafqazinfo.az', 'NEWS', 'https://qafqazinfo.az/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) = lower('QAFQAZINFO_AZ')
		OR source.base_url = 'https://qafqazinfo.az/'
		OR source.base_url = 'https://qafqazinfo.az'
);

UPDATE core.sources
SET code = 'QAFQAZINFO_AZ',
	name = 'Qafqazinfo.az',
	type = 'NEWS',
	base_url = 'https://qafqazinfo.az/',
	updated_at = now()
WHERE lower(code) = lower('QAFQAZINFO_AZ')
	OR base_url = 'https://qafqazinfo.az/'
	OR base_url = 'https://qafqazinfo.az';

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, 'AZ'
FROM core.sources source
WHERE lower(source.code) = lower('QAFQAZINFO_AZ')
	OR source.base_url = 'https://qafqazinfo.az/'
	OR source.base_url = 'https://qafqazinfo.az'
ON CONFLICT DO NOTHING;

INSERT INTO core.authors (id, source_id, username, external_id, profile_url, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000007', source.id, 'qafqazinfo.az', 'qafqazinfo.az', 'https://qafqazinfo.az/', now(), now()
FROM core.sources source
WHERE lower(source.code) = lower('QAFQAZINFO_AZ')
	OR source.base_url = 'https://qafqazinfo.az/'
	OR source.base_url = 'https://qafqazinfo.az'
ON CONFLICT (source_id, username) DO NOTHING;
