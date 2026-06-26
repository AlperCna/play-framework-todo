# Phase 1 Data Model: Exclusion (Allowlist) Registration

Authoritative column contract: `docs/migration_final_schema/migration_final_schema.md` (v5 §6.4) and
`app/migrations/drp-postgres/V001__asset_layer_up.sql`. This document captures only the one entity this
slice touches and the validation/mapping rules derived from the spec. The table already exists (V001);
**no migration is authored here**.

---

## Entity: Exclusion (`exclusions`)

An allowlist entry declaring a domain/URL the platform must never flag, belonging to exactly one
protected entity in this slice.

| Field | DB column | Type | Null | Default | Notes |
|---|---|---|---|---|---|
| id | `id` | BIGSERIAL | NOT NULL | auto | PK; assigned by DB |
| entityId | `entity_id` | BIGINT | NULL (DB) | — | FK → `entities(id)` ON DELETE RESTRICT. **Required in this slice** (domain `Long`); Slick row is `Option[Long]` to match the nullable column (D7). Global (`null`) exclusions out of scope. |
| value | `value` | TEXT | NOT NULL | — | stored exactly as entered (no normalization, FR-007) |
| matchType | `match_type` | TEXT | NOT NULL | — | CHECK IN ('exact','registrable_domain','subdomain_of','pattern'); sealed ADT + codec |
| reason | `reason` | TEXT | NOT NULL | — | open classification, **no DB CHECK**; non-blank, stored as entered |
| isActive | `is_active` | BOOLEAN | NOT NULL | `true` | new exclusions active by default; not toggled in this slice |
| createdBy | `created_by` | TEXT | NOT NULL | `'system'` | DB default; no current-user seam yet (insert omits it) |
| createdAt | `created_at` | TIMESTAMPTZ | NOT NULL | `now()` | read-only (DB default) |
| updatedAt | `updated_at` | TIMESTAMPTZ | NOT NULL | `now()` | read-only; **DB trigger-maintained** |

**Domain type**:
`Exclusion(id: Long, entityId: Long, value: String, matchType: ExclusionMatchType, reason: String, isActive: Boolean, createdBy: String, createdAt: Instant, updatedAt: Instant)`
built via a smart constructor returning `Either[AssetDomainError, Exclusion]`. On `create`, `id`/audit
fields are placeholders (DB assigns them; `isActive = true`, `createdBy = "system"` reflect the DB
defaults).

**`ExclusionMatchType`** (sealed ADT + codec):
`Exact ↔ "exact"`, `RegistrableDomain ↔ "registrable_domain"`, `SubdomainOf ↔ "subdomain_of"`,
`Pattern ↔ "pattern"`. `fromValue(raw): Option[ExclusionMatchType]` (or `Either`) drives the
`InvalidMatchType` rejection. `match_type = "pattern"` stores `value` verbatim — no pattern validation
(spec edge case).

**Validation rules** (in `Exclusion.create`, surfaced as visible messages, nothing persisted):
- `value` MUST be non-blank → else `AssetDomainError.BlankExclusionValue` (FR-002).
- `reason` MUST be non-blank → else `AssetDomainError.BlankExclusionReason` (FR-002; reason is NOT NULL,
  no DB default — Clarification 2026-06-26).
- `matchType` MUST be one of the four allowed values → else `AssetDomainError.InvalidMatchType(raw)`
  (FR-004). Backed by the DB CHECK `ck_exclusions_match_type`.
- `entityId` MUST reference an existing entity → else `AssetDomainError.UnknownEntity(entityId)` (FR-003).
  Enforced in the service via `EntityRepository.existsById`; backed by the FK.
- Active duplicate `(entity_id, value, match_type)` MUST be prevented → `AssetDomainError`
  `.DuplicateActiveExclusion(entityId, value, matchType)` (FR-006). Service pre-check + DB partial unique
  index backstop.

**Uniqueness**: active exclusions unique per `(entity_id, value, match_type)` while
`is_active = true AND entity_id IS NOT NULL` (V001 index `uq_exclusions_entity_active_value_match`). The
global variant `uq_exclusions_global_active_value_match` (`entity_id IS NULL`) is not exercised here.

**Lifecycle**: create + read only in this slice (no edit/delete/deactivation; `is_active` set `true` at
creation and not toggled).

---

## Domain errors (`AssetDomainError` — additive)

Reuses the existing sealed trait. New cases added (existing `UnknownEntity` reused for FR-003):

| Case | code | message (visible) | Requirement |
|---|---|---|---|
| `BlankExclusionValue` | `drp.asset.error.blankExclusionValue` | "Exclusion value cannot be blank." | FR-002 |
| `BlankExclusionReason` | `drp.asset.error.blankExclusionReason` | "Exclusion reason cannot be blank." | FR-002 |
| `InvalidMatchType(value)` | `drp.asset.error.invalidMatchType` | "'{value}' is not a valid match type." | FR-004 |
| `DuplicateActiveExclusion(entityId, value, matchType)` | `drp.asset.error.duplicateActiveExclusion` | "An active exclusion '{value}' ({matchType}) already exists under entity {entityId}." | FR-006 |
| `UnknownEntity(entityId)` *(existing)* | `drp.asset.error.unknownEntity` | "No entity exists with id {entityId}." | FR-003 |

---

## Repository port (`ExclusionRepository`) — operations used by this slice

| Method | Purpose | Data-access note |
|---|---|---|
| `create(exclusion): Future[Exclusion]` | Insert `entity_id, value, match_type, reason`; return with DB-assigned `id` + read-back audit fields. | Single insert; omits DB-managed columns (D8). |
| `listByEntity(entityId): Future[Seq[Exclusion]]` | All exclusions for one entity, ordered by `id`. | One bulk query — no per-row call (Constitution V). |
| `existsActiveDuplicate(entityId, value, matchType): Future[Boolean]` | Friendly duplicate pre-check. | One query; DB partial unique index is the backstop (D6). |

Owning-entity existence reuses the existing `EntityRepository.existsById` (no new entity-layer method).

## Derived rules & constraints used by this slice (already in V001 — not created here)

- Partial unique index `uq_exclusions_entity_active_value_match = UNIQUE(entity_id, value, match_type) WHERE is_active = true AND entity_id IS NOT NULL` — the duplicate-active-exclusion guard.
- FK `exclusions.entity_id → entities(id)` ON DELETE RESTRICT.
- CHECK `ck_exclusions_match_type = match_type IN ('exact','registrable_domain','subdomain_of','pattern')`.
- `created_by` NOT NULL DEFAULT `'system'`.
- `set_updated_at()` BEFORE UPDATE trigger on `exclusions`.
- Index `ix_exclusions_entity_active = (entity_id, is_active)` (supports the per-entity listing/read).

## State transitions

None in this slice — exclusions are created and read; no status field, edit, or deactivation.
