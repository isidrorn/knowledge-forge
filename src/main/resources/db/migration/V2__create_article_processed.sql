CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE article_processed (
                                   id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
                                   article_raw_id   uuid         NOT NULL UNIQUE
                                       REFERENCES article_raw(id),
                                   tldr             text         NOT NULL,
                                   key_points       text[],
                                   tags             text[],
                                   difficulty       smallint     CHECK (difficulty BETWEEN 1 AND 5),
                                   markdown_content text         NOT NULL,
                                   embedding        vector(1536),
                                   model_used       varchar(100),
                                   status           varchar(20)  NOT NULL DEFAULT 'PROCESSED',
                                   created_at       timestamptz  NOT NULL DEFAULT now(),
                                   published_at     timestamptz
);

-- GIN para búsqueda eficiente por tag: WHERE 'spring' = ANY(tags)
CREATE INDEX idx_processed_tags ON article_processed USING gin(tags);
-- HNSW para similitud vectorial (activar cuando haya embeddings)
-- CREATE INDEX idx_embedding_hnsw ON article_processed
--   USING hnsw (embedding vector_cosine_ops);