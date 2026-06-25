BEGIN;

DROP TRIGGER IF EXISTS trg_exclusions_set_updated_at ON exclusions;
DROP TRIGGER IF EXISTS trg_assets_set_updated_at ON assets;
DROP TRIGGER IF EXISTS trg_asset_groups_set_updated_at ON asset_groups;
DROP TRIGGER IF EXISTS trg_entities_set_updated_at ON entities;

DROP TABLE IF EXISTS exclusions;
DROP TABLE IF EXISTS assets;
DROP TABLE IF EXISTS asset_groups;
DROP TABLE IF EXISTS entities;

DROP FUNCTION IF EXISTS set_updated_at();

COMMIT;

