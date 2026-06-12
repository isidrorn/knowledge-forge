CREATE TABLE pipeline_status_log (
                                     id             bigserial    PRIMARY KEY,
                                     article_raw_id uuid         NOT NULL REFERENCES article_raw(id),
                                     from_status    varchar(20),
                                     to_status      varchar(20)  NOT NULL,
                                     reason         text,
                                     changed_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_status_log_article ON pipeline_status_log (article_raw_id, changed_at);

CREATE TABLE outbox_events (
                               id                   uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
                               article_processed_id uuid         NOT NULL REFERENCES article_processed(id),
                               event_type           varchar(50)  NOT NULL,
                               payload              jsonb        NOT NULL,
                               status               varchar(20)  NOT NULL DEFAULT 'PENDING',
                               attempts             smallint     NOT NULL DEFAULT 0,
                               created_at           timestamptz  NOT NULL DEFAULT now(),
                               sent_at              timestamptz
);
-- El scheduler lee WHERE status = 'PENDING' ORDER BY created_at
CREATE INDEX idx_outbox_pending ON outbox_events (status, created_at)
    WHERE status = 'PENDING';