BEGIN;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE entities (
  id         BIGSERIAL PRIMARY KEY,
  name       TEXT        NOT NULL,
  type       TEXT        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE asset_groups (
  id         BIGSERIAL PRIMARY KEY,
  entity_id  BIGINT      NOT NULL,
  name       TEXT        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_asset_groups_entity
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE RESTRICT
);

CREATE TABLE assets (
  id             BIGSERIAL PRIMARY KEY,
  entity_id      BIGINT      NOT NULL,
  asset_group_id BIGINT,
  asset_type     TEXT        NOT NULL,
  value          TEXT        NOT NULL,
  metadata       JSONB       NOT NULL DEFAULT '{}'::jsonb,
  is_active      BOOLEAN     NOT NULL DEFAULT true,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_assets_entity
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE RESTRICT,
  CONSTRAINT fk_assets_asset_group
    FOREIGN KEY (asset_group_id) REFERENCES asset_groups(id) ON DELETE RESTRICT,
  CONSTRAINT ck_assets_asset_type
    CHECK (asset_type IN ('domain', 'subdomain'))
);

CREATE TABLE exclusions (
  id         BIGSERIAL PRIMARY KEY,
  entity_id  BIGINT,
  value      TEXT        NOT NULL,
  match_type TEXT        NOT NULL,
  reason     TEXT        NOT NULL,
  is_active  BOOLEAN     NOT NULL DEFAULT true,
  created_by TEXT        NOT NULL DEFAULT 'system',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_exclusions_entity
    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE RESTRICT,
  CONSTRAINT ck_exclusions_match_type
    CHECK (match_type IN ('exact', 'registrable_domain', 'subdomain_of', 'pattern'))
);

CREATE UNIQUE INDEX uq_asset_groups_entity_name
ON asset_groups(entity_id, name);

CREATE UNIQUE INDEX uq_assets_active_entity_type_value
ON assets(entity_id, asset_type, value)
WHERE is_active = true;

CREATE INDEX ix_assets_entity_type_value
ON assets(entity_id, asset_type, value);

CREATE INDEX ix_exclusions_entity_active
ON exclusions(entity_id, is_active);

CREATE UNIQUE INDEX uq_exclusions_entity_active_value_match
ON exclusions(entity_id, value, match_type)
WHERE is_active = true AND entity_id IS NOT NULL;

CREATE UNIQUE INDEX uq_exclusions_global_active_value_match
ON exclusions(value, match_type)
WHERE is_active = true AND entity_id IS NULL;

CREATE TRIGGER trg_entities_set_updated_at
BEFORE UPDATE ON entities
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_asset_groups_set_updated_at
BEFORE UPDATE ON asset_groups
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_assets_set_updated_at
BEFORE UPDATE ON assets
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_exclusions_set_updated_at
BEFORE UPDATE ON exclusions
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

COMMIT;
