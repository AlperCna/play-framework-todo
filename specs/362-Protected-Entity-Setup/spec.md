# Feature Specification: Protected Entity Setup — Entity, Asset, Asset Group & Exclusion Management

**Feature Branch**: `feature/362-Protected-Entity-Setup`

**Created**: 2026-06-29

**Type**: Feature Implementation

**Status**: Draft

**Input**: User story `US_Entity_Asset_Exclusion_Setup_EN.md` (US ID 362) — set up a protected entity end-to-end (entity, assets, asset groups, exclusions) via server-rendered CRUD, as the foundational data layer every downstream detection step anchors on.

## Clarifications

### Session 2026-06-29

- Q: Should the new setup screens sit behind an authentication gate, or be reachable without login in this feature? → A: No access gate this feature — screens are reachable without login; authentication is deferred to a later DRP auth seam (the only existing login lives in the to-be-removed `app/todo` scaffold, and reusing it would violate Constitution I).
- Q: Should the list screens paginate? → A: The entity (top-level) list is paginated (page-at-a-time); a single entity's asset list and exclusion list are loaded in full (each is bounded by one parent entity — Constitution IV's stated exemption).
- Q: How is asset metadata entered — discrete fields or raw JSON? → A: Discrete reference fields for the four known keys (homepage URL, login page URL, logo reference, favicon reference); the system assembles the stored metadata from them (no raw-JSON entry), which structurally prevents malformed / binary / oversized content.
- Q (plan-time, vs the authoritative v5 schema): asset-uniqueness scope and entity-name uniqueness? → A: Assets are unique on `(entity, asset_type, value)` among active rows (the schema's index; FR-004 reconciled). Entity-name global uniqueness (FR-002) is enforced by a **new migration `V007`** — the v5 baseline omitted it, and Constitution V wants a race-safe DB guard.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register & view a protected entity (Priority: P1)

An analyst registers a brand to protect by creating an **entity** (name + type), then sees it in the entity list and can reopen it.

**Why this priority**: The entity is the anchor the whole system hangs on — no asset, exclusion, or downstream record can exist without it. With just this, the product already has a working onboarding foundation (the MVP slice).

**Independent Test**: On the create screen, add entity name="Akbank", type="brand"; confirm it appears in the entity list with the right name/type and can be reopened.

**Acceptance Scenarios**:

1. **Given** the entity list is empty, **When** the analyst submits name="Akbank", type="brand", **Then** a new entity is stored and shown in the list.
2. **Given** an entity "Akbank" exists, **When** the analyst submits another entity named "Akbank", **Then** it is rejected with a duplicate-name error and nothing is written.
3. **Given** the create form, **When** the analyst submits a blank name, **Then** an inline validation error is shown and nothing is written.

---

### User Story 2 - Register & view assets under an entity (Priority: P2)

Under an existing entity, the analyst records the digital **assets** to protect (domain/subdomain values) plus reference metadata about the legitimate site.

**Why this priority**: Assets are what detection later compares candidates against; an entity with no assets drives nothing. Needed immediately after the entity.

**Independent Test**: Under "Akbank", create an asset (asset_type="domain", value="akbank.com", metadata: homepage/login/logo/favicon references); confirm it persists active, with no reference DOM summary, listed under the entity; a second asset with no group also succeeds.

**Acceptance Scenarios**:

1. **Given** entity "Akbank", **When** the analyst creates an asset value="akbank.com", type="domain" with reference metadata, **Then** it is stored active, has no reference DOM summary, and is listed under the entity.
2. **Given** an asset value="akbank.com" exists under "Akbank", **When** the analyst creates another asset value="akbank.com" under the same entity, **Then** it is rejected with a duplicate error and nothing is written.
3. **Given** the asset form, **When** the analyst submits with a missing/invalid parent entity, **Then** an error is shown and nothing is written.
4. **Given** the asset form, **When** the analyst submits a blank value, **Then** an inline error is shown and nothing is written.

---

### User Story 3 - Declare exclusions (allowlist) under an entity (Priority: P2)

The analyst declares **exclusions** — domains that are owned-but-unmonitored or legitimate third parties — so the future discovery step has an allowlist. Entries are stored verbatim and never evaluated here.

**Why this priority**: Exclusions are the allowlist the discovery feature depends on; defining + storing them and the read seam is foundational, on par with assets.

**Independent Test**: Under "Akbank", create exclusion value="akbankdirekt.com", match_type="exact", reason="owned_unmonitored"; confirm it persists with created_by="system", stored verbatim; confirm the "active exclusions for entity" read returns it and applies no matching.

**Acceptance Scenarios**:

1. **Given** entity "Akbank", **When** the analyst creates an exclusion (value, match_type, reason), **Then** it is stored verbatim with created_by="system", active, and is not matched/evaluated against anything.
2. **Given** exclusions exist for an entity, **When** the "active exclusions for entity X" read seam is invoked, **Then** it returns exactly that entity's active exclusions and applies no matching logic.
3. **Given** the exclusion form, **When** the analyst submits with no parent entity, **Then** an error is shown and nothing is written.
4. **Given** the exclusion form, **When** the analyst submits a blank value, **Then** an inline error is shown and nothing is written.

---

### User Story 4 - Organize assets into asset groups (Priority: P3)

Optionally, the analyst creates **asset groups** under an entity and assigns assets to a group; an ungrouped asset is equally valid.

**Why this priority**: Organization/convenience only — the system is fully functional without grouping. Lowest priority.

**Independent Test**: Under "Akbank", create group "Akbank Direkt"; assign an existing asset to it; confirm the link; an asset with no group is also valid.

**Acceptance Scenarios**:

1. **Given** entity "Akbank", **When** the analyst creates asset group "Akbank Direkt", **Then** it is stored under the entity.
2. **Given** a group "Akbank Direkt" under "Akbank" and an asset under "Akbank", **When** the analyst assigns the asset to the group, **Then** the asset is linked to the group.
3. **Given** a group that belongs to a different entity, **When** the analyst tries to assign an asset to it, **Then** it is prevented (a group is scoped to its own entity).

---

### Cross-cutting - Edit any record (no delete)

**Acceptance Scenarios**:

1. **Given** any existing record (entity/asset/group/exclusion), **When** the analyst edits visible fields, **Then** the changes and the last-updated timestamp are saved and the created timestamp is unchanged.
2. **Given** any screen, **When** the analyst looks for a delete action, **Then** none exists (create + edit only).

### Edge Cases

- Assigning an asset to an asset group that belongs to a *different* entity → prevented.
- enum-like fields (type / asset_type / match_type / reason): a recognized value persists and re-renders; an unrecognized value is tolerated as raw (open-enum).
- metadata is entered as discrete reference fields (homepage URL, login page URL, logo reference, favicon reference) and assembled by the system, so it is structurally well-formed and cannot carry binary/base64 or oversized blobs (no raw-JSON entry by the analyst).
- Editing never changes the created timestamp.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST let an analyst create an entity with a name and a type; the new entity appears in the entity list.
- **FR-002**: Entity name MUST be globally unique; a duplicate name is rejected with a clear error and nothing is written.
- **FR-003**: System MUST let an analyst create an asset under an existing entity with an asset type, a value, an optional asset group, and reference metadata (homepage URL, login page URL, logo reference, favicon reference); a new asset defaults to active and has no reference DOM summary.
- **FR-004**: An asset MUST be unique on (entity, asset type, value) among active assets; a duplicate (same entity + same asset type + same value) is rejected and nothing is written. (Per the v5 schema's `(entity_id, asset_type, value) WHERE is_active` unique index — CLAUDE.md §4; reconciled from the earlier "(entity, value)" wording during planning.)
- **FR-005**: An asset MAY be assigned to an asset group only if that group belongs to the same entity; assigning a group from a different entity is prevented.
- **FR-006**: System MUST let an analyst create an asset group under an existing entity; an asset MAY reference a group or none.
- **FR-007**: System MUST let an analyst create an exclusion under an existing entity with a value, a match type, and a reason; it is stored verbatim and never evaluated/matched in this feature; created_by is set to the constant "system" and it defaults to active.
- **FR-008**: Exclusions MUST be entity-scoped — a parent entity is required; entity-less (global) exclusions cannot be created here.
- **FR-009**: Creating any child record (asset, asset group, exclusion) with a missing or invalid parent entity MUST be rejected with a clear error and nothing written.
- **FR-010**: Required-field validation MUST reject a blank entity name, blank asset value, or blank exclusion value with an inline error and write no partial record.
- **FR-011**: System MUST let an analyst edit any record; visible fields and the last-updated timestamp change while the created timestamp stays unchanged.
- **FR-012**: The system MUST NOT offer any delete action (hard or soft) anywhere — create and edit only; the active flag stays at its default and is not a user-managed toggle.
- **FR-013**: enum-like fields (entity type, asset type, exclusion match type, exclusion reason) MUST round-trip as text — recognized values persist and re-render correctly; an unrecognized value is tolerated as-is (open enum).
- **FR-014**: The feature MUST expose a read seam returning the active exclusions for a given entity, and a way to resolve an entity together with its assets, for later consumption by the discovery feature; no matching logic is applied here.
- **FR-015**: All create / edit / list / view interactions MUST be delivered as server-rendered web screens.
- **FR-016**: Asset metadata MUST hold references only (URLs / identifiers) and is captured via discrete reference fields (homepage URL, login page URL, logo reference, favicon reference) assembled by the system — the analyst does NOT enter raw JSON; binary content (e.g., logo/favicon image bytes) MUST NOT be stored, only references.
- **FR-017**: The entity (top-level) list MUST be paginated (page-at-a-time). A single entity's asset list and exclusion list MAY be shown in full, each being bounded by one parent entity (Constitution IV's small/fixed-bound exemption).

### Preserved Behaviors *(MUST stay unchanged)*

- Greenfield foundation — there is no prior runtime behavior to preserve. The binding constraints instead are: (a) conform to the agreed v5 data model (table/field names, FK directions, nullability) so later features attach without rework, and (b) keep the module boundary clean — no detection pipeline, matching, site-fetching, or async/queue concern may leak into this module. (See Constraints.)

### Key Entities

- **Entity**: a protected brand/organization; the anchor every other record references. Attributes: name (globally unique), type (brand | person | institution | …).
- **Asset**: a digital property to protect, owned by exactly one entity. Attributes: asset type (domain | subdomain), value (unique per entity), optional asset-group membership, reference metadata (homepage URL, login page URL, logo reference, favicon reference), active flag (default active). No reference DOM summary in this feature.
- **Asset Group**: an optional grouping of assets within a single entity. Attributes: name; scoped to its entity. Assets reference it optionally.
- **Exclusion**: an allowlist entry under an entity. Attributes: value (stored verbatim), match type (exact | registrable_domain | subdomain_of | pattern), reason (manual | owned_unmonitored | third_party_legit | …), active flag, created_by ("system"). Never evaluated in this feature.

## Scope *(mandatory)*

- **In scope**:
  - A new setup module owning the four record types (entity, asset, asset group, exclusion) and their create / edit / list / view screens.
  - Schema for `entities`, `assets`, `asset_groups`, `exclusions` per v5 (fields, FK directions, nullability), created/updated timestamps on all four, recommended indexes (`assets(entity_id, asset_type, value)`, `exclusions(entity_id, is_active)`), and uniqueness: `entities.name` (global, via `V007`), `assets(entity_id, asset_type, value) WHERE is_active` (per entity, among active assets).
  - The read seam ("active exclusions for an entity", "resolve entity + its assets") — defined here; consumed later.
- **Out of scope** (MUST NOT be touched):
  - Any deletion — hard or soft/deactivation. Create + edit only; active flag not user-managed.
  - Global / entity-less exclusions (column stays nullable for the future; only entity-scoped created here).
  - Evaluating exclusion match-type semantics (matching belongs to the discovery feature, US-2).
  - Deriving the reference DOM summary (fetching/analyzing the official site) — deferred to the crawler / feature-extraction side.
  - `candidate_discoveries`, `candidates`, and the entire downstream pipeline (crawl, page features, similarity, signals, scoring, review, cases, evidence, blob storage).
  - PGMQ / JobQueue / any async worker — this feature is fully synchronous.
  - Auth / RBAC / multi-tenant isolation / current-user machinery beyond `created_by = "system"`.
  - DB-level CHECK constraints on metadata / JSONB (references-only is a code-level convention).
  - Storing binary logo/favicon content; only references live in metadata.
  - Asset-group hierarchy navigation, group-based pricing, automatic subdomain discovery.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An analyst can onboard a brand end-to-end in a single session — create an entity, at least one asset (with reference metadata), at least one exclusion, and optionally a group — entirely through the screens.
- **SC-002**: 100% of duplicate attempts (a second entity with an existing name; a second asset with an existing value under the same entity) are rejected with a visible error and zero data written.
- **SC-003**: 100% of child-record creations with a missing/invalid parent entity are rejected with zero data written.
- **SC-004**: Editing any record always changes the last-updated timestamp and never the created timestamp.
- **SC-005**: No screen exposes any delete action.
- **SC-006**: The "active exclusions for an entity" read returns exactly that entity's active exclusions, with no matching/evaluation applied.
- **SC-007**: Every enum-like value entered (recognized or not) re-renders identically after save (round-trip integrity).
- **SC-008**: Exclusions are stored verbatim — the stored value equals what the analyst entered, character for character.

## Constraints / Imposed Decisions

- **Tech (imposed):** Scala Play Framework with Twirl server-rendered templates (no React / SPA), PostgreSQL, modular-monolith architecture.
- **Agreed target schema:** the v5 data model (table/field names, FK directions, nullability) — the implementation MUST conform so later features attach without rework. This module owns only the four setup tables.
- **`exclusions.created_by`:** populated with the constant "system" until the auth/current-user seam lands; the column is unchanged in v5.
- **Access control:** the new screens are reachable without an authentication gate in this feature; no login / current-user is wired (consistent with auth being out of scope, and avoids coupling to the `app/todo` scaffold per Constitution I). A gate is added when the DRP auth seam lands.
- **No DB CHECK on metadata/JSONB:** "metadata is small / references-only" is a code-level convention, not DB-enforced.
- **Consumed dependency:** this module becomes a dependency of the future discovery module; its public read seam is defined deliberately here even though consumption is out of scope. (Later cross-module reads go through the owning module's port per Constitution I.)

## Assumptions

- The v5 16-table schema is the agreed target; this feature implements only the four setup tables from it.
- Single-analyst, single-tenant context; `entity_id` is the owner anchor; no multi-tenant isolation yet.
- `is_active` (assets / exclusions) defaults to active and is not user-editable in this feature.
- Logo and favicon are references in asset metadata, not separate assets and not binary storage.
- All operations are synchronous; no queue/worker is involved.
