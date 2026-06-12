-- truncate-all.sql
-- Limpia todas las tablas del pipeline respetando el orden de FK
-- Ejecutar desde pgAdmin o: psql -U postgres -d aipipeline -f truncate-all.sql

TRUNCATE TABLE
    outbox_events,
    pipeline_status_log,
    article_processed,
    article_raw
RESTART IDENTITY CASCADE;
