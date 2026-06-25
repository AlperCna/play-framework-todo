BEGIN;

CREATE TABLE candidate_discoveries (
  id                 BIGSERIAL PRIMARY KEY,
  entity_id          BIGINT      NOT NULL,
  asset_id           BIGINT,
  value              TEXT        NOT NULL,
  normalized_value   TEXT        NOT NULL,
  source             TEXT        NOT NULL DEFAULT 'permutation',
  dns_status         TEXT        NOT NULL DEFAULT 'pending',
  http_status_code   INT,
  skip_reason        TEXT,
  failed_check_count INT         NOT NULL DEFAULT 0,
  last_checked_at    TIMESTAMPTZ,
  next_check_at      TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_candidate_discoveries_entity
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE RESTRICT,
  CONSTRAINT fk_candidate_discoveries_asset
    FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE RESTRICT,
  CONSTRAINT ck_candidate_discoveries_dns_status
    CHECK (dns_status IN ('pending', 'active', 'inactive', 'error')),
  CONSTRAINT ck_candidate_discoveries_skip_reason
    CHECK (skip_reason IS NULL OR skip_reason IN ('whitelisted', 'duplicate', 'invalid_format')),
  CONSTRAINT ck_candidate_discoveries_failed_check_count
    CHECK (failed_check_count >= 0),
  CONSTRAINT ck_candidate_discoveries_http_status_code
    CHECK (http_status_code IS NULL OR (http_status_code >= 100 AND http_status_code <= 599))
);

CREATE TABLE candidates (
  id               BIGSERIAL PRIMARY KEY,
  entity_id        BIGINT      NOT NULL,
  discovery_id     BIGINT      NOT NULL,
  source           TEXT        NOT NULL,
  value            TEXT        NOT NULL,
  normalized_value TEXT        NOT NULL,
  status           TEXT        NOT NULL DEFAULT 'validated',
  metadata         JSONB       NOT NULL DEFAULT '{}'::jsonb,
  discovered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_candidates_entity
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE RESTRICT,
  CONSTRAINT fk_candidates_discovery
    FOREIGN KEY (discovery_id) REFERENCES candidate_discoveries(id) ON DELETE RESTRICT,
  CONSTRAINT ck_candidates_status
    CHECK (status IN ('validated', 'crawled', 'analyzed', 'scored', 'reviewed', 'closed', 'eliminated', 'error'))
);

CREATE UNIQUE INDEX uq_candidate_discoveries_entity_normalized
ON candidate_discoveries(entity_id, normalized_value);

CREATE INDEX ix_candidate_discoveries_pending
ON candidate_discoveries(entity_id, created_at)
WHERE dns_status = 'pending' AND skip_reason IS NULL;

CREATE INDEX ix_candidate_discoveries_recheck
ON candidate_discoveries(next_check_at)
WHERE dns_status IN ('inactive', 'error')
  AND skip_reason IS NULL
  AND next_check_at IS NOT NULL;

CREATE INDEX ix_candidate_discoveries_asset
ON candidate_discoveries(asset_id)
WHERE asset_id IS NOT NULL;

CREATE UNIQUE INDEX uq_candidates_entity_normalized_active
ON candidates(entity_id, normalized_value)
WHERE status NOT IN ('closed', 'eliminated');

CREATE INDEX ix_candidates_entity_active_created
ON candidates(entity_id, created_at)
WHERE status IN ('validated', 'crawled', 'analyzed', 'scored', 'reviewed');

CREATE INDEX ix_candidates_status_updated_active
ON candidates(status, updated_at)
WHERE status IN ('validated', 'crawled', 'analyzed', 'scored', 'reviewed');

CREATE TRIGGER trg_candidate_discoveries_set_updated_at
BEFORE UPDATE ON candidate_discoveries
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_candidates_set_updated_at
BEFORE UPDATE ON candidates
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

COMMIT;
