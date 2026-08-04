ALTER TABLE core.admins
	DROP CONSTRAINT uq_admins_singleton_key,
	DROP CONSTRAINT ck_admins_singleton_key,
	DROP COLUMN singleton_key;
