INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
SELECT 'THREADS', 'Threads', 'SOCIAL', 'https://www.threads.com/', true, now(), now()
WHERE NOT EXISTS (
	SELECT 1
	FROM core.sources source
	WHERE lower(source.code) IN (lower('THREADS'), lower('THREADS_COM'), lower('THREADS_NET'), lower('META_THREADS'))
		OR source.base_url IN (
			'https://www.threads.com/',
			'https://www.threads.com',
			'https://threads.com/',
			'https://threads.com',
			'https://www.threads.net/',
			'https://www.threads.net'
		)
);

UPDATE core.sources
SET code = 'THREADS',
	name = 'Threads',
	type = 'SOCIAL',
	base_url = 'https://www.threads.com/',
	is_enabled = true,
	updated_at = now()
WHERE lower(code) IN (lower('THREADS_COM'), lower('THREADS_NET'), lower('META_THREADS'))
	OR base_url IN (
		'https://www.threads.com',
		'https://threads.com/',
		'https://threads.com',
		'https://www.threads.net/',
		'https://www.threads.net'
	);

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, language.value
FROM core.sources source
CROSS JOIN (VALUES ('AZ'), ('RU'), ('EN'), ('TR')) AS language(value)
WHERE lower(source.code) = lower('THREADS')
	OR source.base_url IN ('https://www.threads.com/', 'https://www.threads.com')
ON CONFLICT DO NOTHING;
