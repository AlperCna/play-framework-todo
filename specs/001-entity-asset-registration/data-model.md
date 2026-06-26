# Phase 1 Data Model: Protected Entity & Asset Registration

Authoritative column contract: `docs/migration_final_schema/migration_final_schema.md` (v5) and `app/migrations/drp-postgres/V001__asset_layer_up.sql`. This document captures only the two entities this slice touches and the validation/mapping rules derived from the spec. Tables already exist (V001); **no migration is authored here**.

---

## Entity: Protected Entity (`entities`)

The brand / institution / person being protected — the owner anchor for all downstream records.

| Field | DB column | Type | Null | Default | Notes |
|---|---|---|---|---|---|
| id | `id` | BIGSERIAL | NOT NULL | auto | PK; assigned by DB |
| name | `name` | TEXT | NOT NULL | — | non-blank (app-validated) |
| type | `type` | TEXT | NOT NULL | — | free-text classification; **no DB CHECK** (brand/person/institution/…) |
| createdAt | `created_at` | TIMESTAMPTZ | NOT NULL | `now()` | read-only (DB default) |
| updatedAt | `updated_at` | TIMESTAMPTZ | NOT NULL | `now()` | read-only; **DB trigger-maintained** |

**Domain type**: `Entity(id: Long, name: String, type: String /* EntityType wrapper */, createdAt: Instant, updatedAt: Instant)` built via a smart constructor returning `Either[DomainError, Entity]`.

**Validation rules**:
- `name` MUST be non-blank → else `DomainError.BlankEntityName` (FR-002).
- `type` MUST be non-blank; any value accepted (FR-003). No fixed enumeration.

**Uniqueness**: none on `name` — duplicates allowed (Clarification 2026-06-26). PK only.

**Lifecycle**: create + read only in this slice (no edit/delete/deactivate).

---

## Entity: Asset (`assets`)

A protected digital property (a domain) belonging to exactly one entity.

| Field | DB column | Type | Null | Default | Notes |
|---|---|---|---|---|---|
| id | `id` | BIGSERIAL | NOT NULL | auto | PK |
| entityId | `entity_id` | BIGINT | NOT NULL | — | FK → `entities(id)` ON DELETE RESTRICT |
| assetGroupId | `asset_group_id` | BIGINT | NULL | — | FK → `asset_groups(id)`; **nullable, unused this slice** |
| assetType | `asset_type` | TEXT | NOT NULL | — | CHECK IN ('domain','subdomain'); this slice writes only `domain` |
| value | `value` | TEXT | NOT NULL | — | stored exactly as entered (no normalization, FR-007) |
| metadata | `metadata` | JSONB | NOT NULL | `'{}'` | references/summaries only (FR-006) |
| isActive | `is_active` | BOOLEAN | NOT NULL | `true` | new assets active by default |
| createdAt | `created_at` | TIMESTAMPTZ | NOT NULL | `now()` | read-only |
| updatedAt | `updated_at` | TIMESTAMPTZ | NOT NULL | `now()` | read-only; DB trigger-maintained |

**Domain type**: `Asset(id, entityId, assetGroupId: Option[Long], assetType: AssetType, value: String, metadata: AssetMetadata, isActive: Boolean, createdAt, updatedAt)` via smart constructor → `Either[DomainError, Asset]`.

**`AssetType`** (sealed ADT + codec): `Domain` ↔ `"domain"`, `Subdomain` ↔ `"subdomain"`. This slice creates only `Domain`.

**`AssetMetadata`** (references-only value object, all optional):
`homepageUrl`, `loginPageUrl`, `logoRef`, `faviconRef`, `referenceDomSummary`. Encoded to JSONB; binary/base64 content MUST NOT be accepted (FR-006).

**Validation rules**:
- `value` MUST be non-blank → else `DomainError.BlankAssetValue` (FR-005). No further format/domain check (Clarification 2026-06-26).
- `entityId` MUST reference an existing entity → else `DomainError.UnknownEntity` (FR-005). Enforced in the service (existence check) and backed by the FK.
- Active duplicate `(entity_id, asset_type, value)` MUST be prevented → surfaced as `DomainError.DuplicateActiveAsset` (FR-008). Backed by the partial unique index `uq_assets_active_entity_type_value` (`WHERE is_active = true`).

**Relationships**: `Asset` *N → 1* `Entity` (required). `Asset` *N → 0..1* `AssetGroup` (unused).

---

## Derived rules & constraints used by this slice (already in V001 — not created here)

- Partial unique index `uq_assets_active_entity_type_value = UNIQUE(entity_id, asset_type, value) WHERE is_active = true` — the duplicate-active-asset guard.
- FK `assets.entity_id → entities(id)` ON DELETE RESTRICT.
- `set_updated_at()` BEFORE UPDATE triggers on both tables.
- CHECK `asset_type IN ('domain','subdomain')`.

## State transitions

None in this slice — entities and assets are created and read; no status field, edit, or deactivation. (Asset `is_active` is set to `true` at creation and not toggled here.)
