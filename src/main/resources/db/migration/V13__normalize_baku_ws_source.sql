UPDATE core.sources
SET code = 'BAKU_WS',
	name = 'Baku.ws',
	base_url = 'https://baku.ws/',
	updated_at = now()
WHERE lower(code) = lower('baku_ws')
	OR base_url = 'https://baku.ws'
	OR base_url = 'https://baku.ws/';

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id, 'AZ'
FROM core.sources source
WHERE lower(source.code) = lower('BAKU_WS')
	OR source.base_url = 'https://baku.ws/'
	OR source.base_url = 'https://baku.ws'
ON CONFLICT DO NOTHING;

UPDATE core.authors author
SET external_id = 'baku.ws',
	username = 'baku.ws',
	profile_url = 'https://baku.ws/',
	updated_at = now()
FROM core.sources source
WHERE author.source_id = source.id
	AND (
		lower(source.code) = lower('BAKU_WS')
		OR source.base_url = 'https://baku.ws/'
		OR source.base_url = 'https://baku.ws'
	)
	AND (
		lower(author.username) = lower('baku_ws')
		OR lower(author.external_id) = lower('baku_ws')
		OR lower(author.username) = lower('baku.ws')
	);
