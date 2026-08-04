CREATE SEQUENCE core.admins_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE core.admins (
	id bigint NOT NULL DEFAULT nextval('core.admins_seq'),
	username varchar(100) NOT NULL,
	password_hash varchar(255) NOT NULL,
	role varchar(32) NOT NULL DEFAULT 'ADMIN',
	singleton_key smallint NOT NULL DEFAULT 1,
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_admins PRIMARY KEY (id),
	CONSTRAINT uq_admins_username UNIQUE (username),
	CONSTRAINT uq_admins_singleton_key UNIQUE (singleton_key),
	CONSTRAINT ck_admins_role CHECK (role = 'ADMIN'),
	CONSTRAINT ck_admins_singleton_key CHECK (singleton_key = 1)
);

ALTER SEQUENCE core.admins_seq OWNED BY core.admins.id;

CREATE SEQUENCE core.refresh_tokens_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE core.refresh_tokens (
	id bigint NOT NULL DEFAULT nextval('core.refresh_tokens_seq'),
	admin_id bigint NOT NULL,
	token_hash varchar(64) NOT NULL,
	expires_at timestamp with time zone NOT NULL,
	revoked_at timestamp with time zone,
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
	CONSTRAINT fk_refresh_tokens_admin FOREIGN KEY (admin_id) REFERENCES core.admins (id) ON DELETE CASCADE,
	CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

ALTER SEQUENCE core.refresh_tokens_seq OWNED BY core.refresh_tokens.id;

CREATE INDEX idx_refresh_tokens_admin_id ON core.refresh_tokens (admin_id);
CREATE INDEX idx_refresh_tokens_expires_at ON core.refresh_tokens (expires_at);
