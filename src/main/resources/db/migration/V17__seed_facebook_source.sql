INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'FACEBOOK', 'Facebook', 'SOCIAL', 'https://www.facebook.com/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) IN (lower('FACEBOOK'), lower('FB'), lower('FACEBOOK_COM'), lower('META_FACEBOOK'))
		OR source.base_url IN (
			'https://www.facebook.com/',
			'https://www.facebook.com',
			'https://facebook.com/',
			'https://facebook.com',
			'https://m.facebook.com/',
			'https://m.facebook.com'
		)
);

UPDATE core.sources
SET code = 'FACEBOOK',
	name = 'Facebook',
	type = 'SOCIAL',
	base_url = 'https://www.facebook.com/',
	is_enabled = true,
	updated_at = now()
WHERE lower(code) IN (lower('FB'), lower('FACEBOOK_COM'), lower('META_FACEBOOK'))
	OR base_url IN (
		'https://www.facebook.com',
		'https://facebook.com/',
		'https://facebook.com',
		'https://m.facebook.com/',
		'https://m.facebook.com'
	);

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, language.value
FROM core.sources source
CROSS JOIN (VALUES ('AZ'), ('RU'), ('EN'), ('TR')) AS language(value)
WHERE lower(source.code) = lower('FACEBOOK')
	OR source.base_url IN ('https://www.facebook.com/', 'https://www.facebook.com')
ON CONFLICT DO NOTHING;
