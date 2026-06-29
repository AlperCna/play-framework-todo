# Data Model — Protected Entity Setup (362)

Authoritative DDL source: `app/migrations/drp-postgres/V001__asset_layer_up.sql` (+ `V007` for `entities.name` UNIQUE). Domain types below are the in-code (pure `domain`) representation; no Play/Slick/JSON types leak into `domain`.

## Typed IDs & enums

- **IDs** (wrap `Long`, illegal-states-unrepresentable): `EntityId`, `AssetGroupId`, `AssetId`, `ExclusionId`. New (unsaved) rows use a sentinel/`None` until the DB assigns `BIGSERIAL`.
- **`EntityType`** — *open* enum (no DB CHECK): `Brand`, `Person`, `Institution`, … + `Other(raw)` fallback. String codec: recognized → typed, unrecognized → `Other(raw)` (FR-013).
- **`AssetType`** — *closed* enum (DB CHECK `domain|subdomain`): `Domain`, `Subdomain`. Codec rejects unknown before insert.
- **`MatchType`** — *closed* enum (DB CHECK `exact|registrable_domain|subdomain_of|pattern`): `Exact`, `RegistrableDomain`, `SubdomainOf`, `Pattern`.
- **`ExclusionReason`** — *open* enum (no DB CHECK): `Manual`, `OwnedUnmonitored`, `ThirdPartyLegit`, … + `Other(raw)`.
- **`AssetMetadata`** — references only: `homepageUrl: Option[String]`, `loginPageUrl: Option[String]`, `logoRef: Option[String]`, `faviconRef: Option[String]`. Serialized to JSONB `assets.metadata` via `AssetMetadataCodec` (no binary, no raw-JSON entry).

## Entity: `entities`

| Field | DB type | Domain | Notes |
|---|---|---|---|
| id | BIGSERIAL PK | `EntityId` | |
| name | TEXT NOT NULL | `String` (non-blank) | **UNIQUE (global)** via `V007` |
| type | TEXT NOT NULL | `EntityType` | open enum |
| created_at | TIMESTAMPTZ | `Instant` | DB default `now()` |
| updated_at | TIMESTAMPTZ | `Instant` | DB trigger `set_updated_at()` |

- **Smart ctor** `Entity.create(name, type)` → `Either[DomainError, Entity]`: `name` non-blank (else `EmptyEntityName`).
- **Uniqueness**: `name` global (V007). Service pre-checks for a friendly duplicate message; the unique index is the authoritative guard.

## Entity: `asset_groups`

| Field | DB type | Domain | Notes |
|---|---|---|---|
| id | BIGSERIAL PK | `AssetGroupId` | |
| entity_id | BIGINT NOT NULL | `EntityId` | FK → entities, ON DELETE RESTRICT |
| name | TEXT NOT NULL | `String` (non-blank) | UNIQUE `(entity_id, name)` |
| created_at / updated_at | TIMESTAMPTZ | `Instant` | trigger-maintained |

- **Smart ctor** `AssetGroup.create(entityId, name)` → `Either[DomainError, AssetGroup]`: `name` non-blank.
- Parent `entity_id` existence is validated at the **service** layer (FR-009), not in `domain`.

## Entity: `assets`

| Field | DB type | Domain | Notes |
|---|---|---|---|
| id | BIGSERIAL PK | `AssetId` | |
| entity_id | BIGINT NOT NULL | `EntityId` | FK → entities, RESTRICT |
| asset_group_id | BIGINT NULL | `Option[AssetGroupId]` | FK → asset_groups, RESTRICT |
| asset_type | TEXT NOT NULL CHECK | `AssetType` | domain/subdomain |
| value | TEXT NOT NULL | `String` (non-blank) | |
| metadata | JSONB NOT NULL `'{}'` | `AssetMetadata` | references only |
| is_active | BOOLEAN NOT NULL `true` | `Boolean` (always true here) | no UI toggle |
| created_at / updated_at | TIMESTAMPTZ | `Instant` | trigger-maintained |

- **Smart ctor** `Asset.create(entityId, assetType, value, group, metadata)` → `Either[DomainError, Asset]`: `value` non-blank; `is_active=true`; no `reference_dom_summary`.
- **Uniqueness**: `(entity_id, asset_type, value) WHERE is_active=true` (V001 `uq_assets_active_entity_type_value`) — the authoritative duplicate guard (FR-004, reconciled).
- **Service checks**: parent `entity_id` exists; if `asset_group_id` set, the group's `entity_id` == the asset's `entity_id` (FR-005, cross-entity group forbidden).

## Entity: `exclusions`

| Field | DB type | Domain | Notes |
|---|---|---|---|
| id | BIGSERIAL PK | `ExclusionId` | |
| entity_id | BIGINT NULL | `EntityId` (required here) | FK → entities, RESTRICT. **Entity-scoped only this feature** (FR-008) — global/NULL not created |
| value | TEXT NOT NULL | `String` (non-blank) | stored verbatim, never evaluated |
| match_type | TEXT NOT NULL CHECK | `MatchType` | exact/registrable_domain/subdomain_of/pattern |
| reason | TEXT NOT NULL | `ExclusionReason` | open enum |
| is_active | BOOLEAN NOT NULL `true` | `Boolean` (always true here) | |
| created_by | TEXT NOT NULL `'system'` | `String` = "system" | constant; no current-user |
| created_at / updated_at | TIMESTAMPTZ | `Instant` | trigger-maintained |

- **Smart ctor** `Exclusion.create(entityId, value, matchType, reason)` → `Either[DomainError, Exclusion]`: `entityId` required; `value` non-blank; `createdBy="system"`.
- **Uniqueness**: `(entity_id, value, match_type) WHERE is_active AND entity_id NOT NULL` (V001) — authoritative guard.
- **Never evaluated**: stored verbatim; no match-type semantics applied (out of scope).

## Relationships

```
entities 1───* asset_groups        (asset_groups.entity_id)
entities 1───* assets              (assets.entity_id)
entities 1───* exclusions          (exclusions.entity_id, entity-scoped this feature)
asset_groups 1───* assets          (assets.asset_group_id, OPTIONAL; same-entity only)
```

All FKs `ON DELETE RESTRICT` (no cascade; no delete in this feature anyway).

## DomainError values (in `drp.shared.domain.DomainError`, sealed)

`EmptyEntityName`, `EmptyAssetValue`, `EmptyAssetGroupName`, `EmptyExclusionValue`, `EntityNotFound(id)`, `AssetGroupNotFound(id)`, `DuplicateEntityName(name)`, `DuplicateAsset(entityId, assetType, value)`, `DuplicateExclusion(...)`, `AssetGroupEntityMismatch(groupEntityId, assetEntityId)`. Each carries a stable `code` (i18n key) + message.

## Read-models (no domain/persistence leak)

- **Web view-models** (`web/*ViewModel`): `EntityViewModel`, `AssetViewModel`, `AssetGroupViewModel`, `ExclusionViewModel` — flat primitives for Twirl.
- **Read-seam read-models** (`application/ports/AssetReadPort`): `ExclusionView(value, matchType, reason)` and `EntityWithAssets(entity, assets)` — typed projections returned to the future discovery module (never the Slick row or raw `domain` entity across the boundary).
