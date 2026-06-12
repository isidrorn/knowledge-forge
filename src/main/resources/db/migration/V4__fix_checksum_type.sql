-- V1__fix_checksum_type.sql (o el número que corresponda)
ALTER TABLE article_raw
ALTER COLUMN content_checksum TYPE varchar(64),
    ADD CONSTRAINT chk_checksum_length
        CHECK (char_length(content_checksum) = 64);