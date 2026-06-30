INSERT INTO core.sources (code, name, type, base_url, is_enabled, created_at, updated_at)
VALUES ('baku_ws', 'baku.ws', 'NEWS', 'https://baku.ws', true, now(), now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.authors (id, source_id, username, external_id, profile_url, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000001', source.id, 'baku.ws', 'baku_ws', 'https://baku.ws', now(), now()
FROM core.sources source
WHERE source.code = 'baku_ws'
ON CONFLICT (source_id, username) DO NOTHING;
