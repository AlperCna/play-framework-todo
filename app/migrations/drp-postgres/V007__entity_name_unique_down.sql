-- V007 down — drop the global entity-name unique index.
BEGIN;

DROP INDEX IF EXISTS uq_entities_name;

COMMIT;
