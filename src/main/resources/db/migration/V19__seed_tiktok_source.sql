INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'TIKTOK', 'TikTok', 'SOCIAL', 'https://www.tiktok.com/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) IN (lower('TIKTOK'), lower('TIKTOK_COM'), lower('TT'), lower('BYTE_DANCE_TIKTOK'))
		OR source.base_url IN (
			'https://www.tiktok.com/',
			'https://www.tiktok.com',
			'https://tiktok.com/',
			'https://tiktok.com'
		)
);

UPDATE core.sources
SET code = 'TIKTOK',
	name = 'TikTok',
	type = 'SOCIAL',
	base_url = 'https://www.tiktok.com/',
	is_enabled = true,
	updated_at = now()
WHERE lower(code) IN (lower('TIKTOK_COM'), lower('TT'), lower('BYTE_DANCE_TIKTOK'))
	OR base_url IN (
		'https://www.tiktok.com',
		'https://tiktok.com/',
		'https://tiktok.com'
	);

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, language.value
FROM core.sources source
CROSS JOIN (VALUES ('AZ'), ('RU'), ('EN'), ('TR')) AS language(value)
WHERE lower(source.code) = lower('TIKTOK')
	OR source.base_url IN ('https://www.tiktok.com/', 'https://www.tiktok.com')
ON CONFLICT DO NOTHING;
