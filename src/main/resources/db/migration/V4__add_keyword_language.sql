ALTER TABLE core.keywords
	ADD COLUMN language varchar(10);

UPDATE core.keywords
SET language = 'AZ'
WHERE language IS NULL;

ALTER TABLE core.keywords
	ALTER COLUMN language SET NOT NULL;

CREATE INDEX idx_keywords_language_enabled
	ON core.keywords (language, is_enabled);
