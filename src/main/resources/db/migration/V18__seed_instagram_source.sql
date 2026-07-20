INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'INSTAGRAM', 'Instagram', 'SOCIAL', 'https://www.instagram.com/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) IN (lower('INSTAGRAM'), lower('INSTAGRAM_COM'), lower('IG'), lower('META_INSTAGRAM'))
		OR source.base_url IN (
			'https://www.instagram.com/',
			'https://www.instagram.com',
			'https://instagram.com/',
			'https://instagram.com'
		)
);

UPDATE core.sources
SET code = 'INSTAGRAM',
	name = 'Instagram',
	type = 'SOCIAL',
	base_url = 'https://www.instagram.com/',
	is_enabled = true,
	updated_at = now()
WHERE lower(code) IN (lower('INSTAGRAM_COM'), lower('IG'), lower('META_INSTAGRAM'))
	OR base_url IN (
		'https://www.instagram.com',
		'https://instagram.com/',
		'https://instagram.com'
	);

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, language.value
FROM core.sources source
CROSS JOIN (VALUES ('AZ'), ('RU'), ('EN'), ('TR')) AS language(value)
WHERE lower(source.code) = lower('INSTAGRAM')
	OR source.base_url IN ('https://www.instagram.com/', 'https://www.instagram.com')
ON CONFLICT DO NOTHING;
