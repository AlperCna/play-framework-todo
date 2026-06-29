-- V007 — Enforce globally-unique entity names (US-362 FR-002).
-- The v5 baseline (V001) omitted this; the US requires it, and it is the race-safe
-- authoritative guard (Constitution V) behind the service-layer duplicate pre-check.
-- Apply manually (scripts/migrate_drp_up.*); the agent never executes migrations.
BEGIN;

CREATE UNIQUE INDEX uq_entities_name ON entities(name);

COMMIT;
