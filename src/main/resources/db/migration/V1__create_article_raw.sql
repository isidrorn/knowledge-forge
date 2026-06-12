CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE article_raw (
                             id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
                             source           varchar(50)  NOT NULL,
                             url              text         UNIQUE,
                             content_checksum char(64),
                             raw_content      text         NOT NULL,
                             status           varchar(20)  NOT NULL DEFAULT 'RECEIVED',
                             retry_count      smallint     NOT NULL DEFAULT 0,
                             received_at      timestamptz  NOT NULL DEFAULT now(),
                             processed_at     timestamptz
);

CREATE INDEX idx_article_raw_checksum ON article_raw (content_checksum);
CREATE INDEX idx_article_raw_status   ON article_raw (status);