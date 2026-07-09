INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'OXU_AZ', 'Oxu.az', 'NEWS', 'https://oxu.az/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) = lower('OXU_AZ')
		OR source.base_url = 'https://oxu.az/'
		OR source.base_url = 'https://oxu.az'
);

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, 'AZ'
FROM core.sources source
WHERE lower(source.code) = lower('OXU_AZ')
	OR source.base_url = 'https://oxu.az/'
	OR source.base_url = 'https://oxu.az'
ON CONFLICT DO NOTHING;

INSERT INTO core.authors (id, source_id, username, external_id, profile_url, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000002', source.id, 'oxu.az', 'oxu.az', 'https://oxu.az/', now(), now()
FROM core.sources source
WHERE lower(source.code) = lower('OXU_AZ')
	OR source.base_url = 'https://oxu.az/'
	OR source.base_url = 'https://oxu.az'
ON CONFLICT (source_id, username) DO NOTHING;
