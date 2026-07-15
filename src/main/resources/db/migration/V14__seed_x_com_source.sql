INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'X_COM', 'X', 'SOCIAL', 'https://x.com/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) IN (lower('X_COM'), lower('TWITTER'), lower('TWITTER_X'), lower('X'))
		OR source.base_url IN ('https://x.com/', 'https://x.com', 'https://twitter.com/', 'https://twitter.com')
);

UPDATE core.sources
SET code = 'X_COM',
	name = 'X',
	type = 'SOCIAL',
	base_url = 'https://x.com/',
	updated_at = now()
WHERE lower(code) IN (lower('TWITTER'), lower('TWITTER_X'), lower('X'))
	OR base_url IN ('https://x.com', 'https://twitter.com/', 'https://twitter.com');

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, language.value
FROM core.sources source
CROSS JOIN (VALUES ('AZ'), ('RU'), ('EN'), ('TR')) AS language(value)
WHERE lower(source.code) = lower('X_COM')
	OR source.base_url IN ('https://x.com/', 'https://x.com')
ON CONFLICT DO NOTHING;
