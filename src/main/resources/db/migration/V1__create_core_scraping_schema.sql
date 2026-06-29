CREATE SCHEMA IF NOT EXISTS core;

CREATE SEQUENCE core.sources_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE core.keywords_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE core.post_medias_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE core.post_keywords_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE core.sources (
	id integer NOT NULL DEFAULT nextval('core.sources_seq'),
	code varchar(100) NOT NULL,
	name varchar(255) NOT NULL,
	type varchar(32) NOT NULL,
	base_url text NOT NULL,
	is_enabled boolean NOT NULL DEFAULT true,
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_sources PRIMARY KEY (id),
	CONSTRAINT uq_sources_code UNIQUE (code),
	CONSTRAINT uq_sources_name UNIQUE (name),
	CONSTRAINT uq_sources_base_url UNIQUE (base_url)
);

ALTER SEQUENCE core.sources_seq OWNED BY core.sources.id;

CREATE INDEX idx_sources_type_enabled ON core.sources (type, is_enabled);

CREATE TABLE core.keywords (
	id integer NOT NULL DEFAULT nextval('core.keywords_seq'),
	word varchar(255) NOT NULL,
	is_enabled boolean NOT NULL DEFAULT true,
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_keywords PRIMARY KEY (id),
	CONSTRAINT uq_keywords_word UNIQUE (word)
);

ALTER SEQUENCE core.keywords_seq OWNED BY core.keywords.id;

CREATE INDEX idx_keywords_enabled ON core.keywords (is_enabled);

CREATE TABLE core.authors (
	id uuid NOT NULL,
	source_id integer NOT NULL,
	username varchar(255) NOT NULL,
	external_id varchar(255),
	profile_url text,
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_authors PRIMARY KEY (id),
	CONSTRAINT fk_authors_source FOREIGN KEY (source_id) REFERENCES core.sources (id),
	CONSTRAINT uq_authors_source_external_id UNIQUE (source_id, external_id),
	CONSTRAINT uq_authors_source_username UNIQUE (source_id, username),
	CONSTRAINT uq_authors_profile_url UNIQUE (profile_url)
);

CREATE INDEX idx_authors_source_id ON core.authors (source_id);
CREATE INDEX idx_authors_source_username ON core.authors (source_id, username);

CREATE TABLE core.posts (
	id uuid NOT NULL,
	source_id integer NOT NULL,
	author_id uuid NOT NULL,
	external_post_id varchar(255),
	post_url text NOT NULL,
	post_date timestamp with time zone NOT NULL,
	scraped_at timestamp with time zone NOT NULL,
	text text NOT NULL,
	text_hash varchar(128) NOT NULL,
	language varchar(16),
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_posts PRIMARY KEY (id),
	CONSTRAINT fk_posts_source FOREIGN KEY (source_id) REFERENCES core.sources (id),
	CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES core.authors (id),
	CONSTRAINT uq_posts_post_url UNIQUE (post_url),
	CONSTRAINT uq_posts_source_external_post_id UNIQUE (source_id, external_post_id)
);

CREATE INDEX idx_posts_source_post_date ON core.posts (source_id, post_date);
CREATE INDEX idx_posts_author_post_date ON core.posts (author_id, post_date);
CREATE INDEX idx_posts_text_hash ON core.posts (text_hash);
CREATE INDEX idx_posts_scraped_at ON core.posts (scraped_at);

CREATE TABLE core.post_medias (
	id bigint NOT NULL DEFAULT nextval('core.post_medias_seq'),
	post_id uuid NOT NULL,
	media_type varchar(32) NOT NULL,
	media_url text NOT NULL,
	position integer NOT NULL,
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_post_medias PRIMARY KEY (id),
	CONSTRAINT fk_post_medias_post FOREIGN KEY (post_id) REFERENCES core.posts (id) ON DELETE CASCADE,
	CONSTRAINT uq_post_medias_post_media_url UNIQUE (post_id, media_url),
	CONSTRAINT uq_post_medias_post_position UNIQUE (post_id, position)
);

ALTER SEQUENCE core.post_medias_seq OWNED BY core.post_medias.id;

CREATE INDEX idx_post_medias_post_id ON core.post_medias (post_id);

CREATE TABLE core.post_keywords (
	id integer NOT NULL DEFAULT nextval('core.post_keywords_seq'),
	post_id uuid NOT NULL,
	keyword_id integer NOT NULL,
	matched_text text,
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_post_keywords PRIMARY KEY (id),
	CONSTRAINT fk_post_keywords_post FOREIGN KEY (post_id) REFERENCES core.posts (id) ON DELETE CASCADE,
	CONSTRAINT fk_post_keywords_keyword FOREIGN KEY (keyword_id) REFERENCES core.keywords (id),
	CONSTRAINT uq_post_keywords_post_keyword UNIQUE (post_id, keyword_id)
);

ALTER SEQUENCE core.post_keywords_seq OWNED BY core.post_keywords.id;

CREATE INDEX idx_post_keywords_post_id ON core.post_keywords (post_id);
CREATE INDEX idx_post_keywords_keyword_id ON core.post_keywords (keyword_id);

CREATE TABLE core.scrape_jobs (
	id uuid NOT NULL,
	source_id integer NOT NULL,
	keyword_id integer NOT NULL,
	date_from date NOT NULL,
	date_to date NOT NULL,
	status varchar(32) NOT NULL,
	started_at timestamp with time zone,
	finished_at timestamp with time zone,
	posts_found integer NOT NULL DEFAULT 0,
	posts_saved integer NOT NULL DEFAULT 0,
	error_message text,
	created_at timestamp with time zone NOT NULL,
	updated_at timestamp with time zone NOT NULL,
	CONSTRAINT pk_scrape_jobs PRIMARY KEY (id),
	CONSTRAINT fk_scrape_jobs_source FOREIGN KEY (source_id) REFERENCES core.sources (id),
	CONSTRAINT fk_scrape_jobs_keyword FOREIGN KEY (keyword_id) REFERENCES core.keywords (id),
	CONSTRAINT ck_scrape_jobs_date_range CHECK (date_to >= date_from),
	CONSTRAINT ck_scrape_jobs_posts_found CHECK (posts_found >= 0),
	CONSTRAINT ck_scrape_jobs_posts_saved CHECK (posts_saved >= 0)
);

CREATE INDEX idx_scrape_jobs_source_status ON core.scrape_jobs (source_id, status);
CREATE INDEX idx_scrape_jobs_keyword_status ON core.scrape_jobs (keyword_id, status);
CREATE INDEX idx_scrape_jobs_created_at ON core.scrape_jobs (created_at);
CREATE INDEX idx_scrape_jobs_started_at ON core.scrape_jobs (started_at);
