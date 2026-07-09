CREATE TABLE core.source_supported_languages (
	source_id integer NOT NULL,
	language varchar(10) NOT NULL,
	CONSTRAINT pk_source_supported_languages PRIMARY KEY (source_id, language),
	CONSTRAINT fk_source_supported_languages_source
		FOREIGN KEY (source_id) REFERENCES core.sources (id) ON DELETE CASCADE
);

CREATE INDEX idx_source_supported_languages_language
	ON core.source_supported_languages (language);

INSERT INTO core.source_supported_languages (source_id, language)
SELECT source.id,
	CASE
		WHEN lower(source.code) LIKE '%media%'
			OR lower(source.base_url) LIKE '%media.az%'
			THEN 'RU'
		ELSE 'AZ'
	END
FROM core.sources source
ON CONFLICT DO NOTHING;
