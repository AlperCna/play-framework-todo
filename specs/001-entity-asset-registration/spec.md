# Feature Specification: Protected Entity & Asset Registration

**Feature Branch**: `001-entity-asset-registration`

**Created**: 2026-06-26

**Type**: Feature Implementation

**Status**: Draft

**Input**: User description: "US-001-Protected-Entity-Asset-Registration.md dosyasındaki hazır user story'yi feature girdisi olarak kullan." (foundational vertical slice — register a protected entity and one or more domain assets under it, viewable on a server-rendered screen)

## Clarifications

### Session 2026-06-26

- Q: Entity name duplicate policy — unique/blocked, warned, or freely allowed? → A: Allowed — no uniqueness constraint on `entities.name` (matches the v5 schema); duplicate entity names may be created freely.
- Q: Asset value validation strictness — any non-empty value, or a domain-format check? → A: Only blank is rejected; any non-empty value is accepted and stored exactly as entered (no format/domain validation in this slice).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register a protected entity (Priority: P1)

An analyst declares *what the platform protects* by registering a protected entity — a brand, institution, or person — providing a name and a classification type. The entity is stored and immediately appears on a listing screen. This is the anchor every later record (assets, candidates, scores, cases) references.

**Why this priority**: Nothing in the detection pipeline can exist without an entity to anchor to. Entity registration alone is a viable, demoable slice — it proves the chosen stack works end to end (form → persistence → server-rendered page) inside the modular monolith.

**Independent Test**: Register entity "Akbank" (type "brand"); confirm it persists and is shown on the listing screen with its name, type, and timestamps. No assets required for this test to pass.

**Acceptance Scenarios**:

1. **Given** an empty system, **When** the analyst registers an entity with name "Akbank" and type "brand", **Then** the entity is stored with an id, name, type, and creation/update timestamps, and it appears on the listing screen.
2. **Given** the entity registration form, **When** the analyst submits a blank name, **Then** the system shows a visible validation message and persists nothing.
3. **Given** the entity registration form, **When** the analyst registers an entity with type "institution" (a non-"brand" value), **Then** the entity is accepted and the type is stored and displayed exactly as entered.

---

### User Story 2 - Register domain assets under an entity (Priority: P2)

Under an existing entity, the analyst registers one or more domain assets (e.g., `akbank.com`) — the concrete digital properties to protect. Each asset is stored as active and listed under its owning entity. Optional reference fields (homepage, login page, logo/favicon references, reference DOM summary) may accompany an asset as *references only*.

**Why this priority**: Assets give the entity something concrete to protect and complete the "protect Akbank / akbank.com" demo. It builds on US1 (an entity must exist first) but is independently testable once an entity is present.

**Independent Test**: With entity "Akbank" present, register domain asset "akbank.com"; confirm it persists under that entity with the correct owner reference, asset type "domain", active status, and timestamps, and appears under the entity on screen.

**Acceptance Scenarios**:

1. **Given** an existing entity "Akbank", **When** the analyst registers a domain asset with value "akbank.com", **Then** an asset is stored under that entity with asset type "domain", active status true, and creation/update timestamps, and it appears under the entity on screen.
2. **Given** an existing entity, **When** the analyst registers an asset whose optional metadata holds only reference fields (homepage URL, login page URL, logo reference, favicon reference, reference DOM summary), **Then** the asset is accepted and stored.
3. **Given** the asset registration form, **When** the analyst submits a blank asset value, **Then** the system shows a visible validation message and persists nothing.
4. **Given** no entity with the referenced id exists, **When** the analyst attempts to register an asset under it, **Then** the system rejects the attempt and persists nothing.

---

### Edge Cases

- **Entity with zero assets**: valid — the entity renders with an empty asset list, not an error.
- **Non-"brand" entity type** (e.g., "institution", "person"): accepted; type is a free-text classification, not a fixed enumeration.
- **Duplicate active asset** (same entity + asset type + value while active): rejected with a visible message; the system does not crash. (Active-asset uniqueness is enforced at the data layer.)
- **Duplicate entity name**: allowed — no uniqueness rule on entity name in the authoritative data model (clarified 2026-06-26; see Clarifications).
- **Binary content in asset metadata**: not accepted — metadata holds references/summaries only; binary/base64 content has no path in this slice.
- **Non-domain-looking asset value** (e.g., contains spaces, or has no dot): accepted and stored as entered — only a blank value is rejected (clarified 2026-06-26).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an analyst to register a protected entity by providing a name and a type, persisting it with creation and update timestamps.
- **FR-002**: The system MUST reject entity registration when the name is blank, persisting nothing and showing a visible validation message.
- **FR-003**: The system MUST accept any non-empty type value as a free-text classification (e.g., "brand", "institution", "person") without enforcing a fixed set of allowed values.
- **FR-004**: The system MUST allow an analyst to register one or more domain assets under an existing entity, each recorded with asset type "domain", the entered value, an active status defaulting to true, and creation/update timestamps.
- **FR-005**: The system MUST reject asset registration when the asset value is blank OR when the referenced entity does not exist, persisting nothing and showing a visible message. No further format/domain validation is applied — any non-blank value is accepted and stored exactly as entered (see FR-007).
- **FR-006**: The system MUST allow an asset's optional reference metadata to carry only reference/summary fields (homepage URL, login page URL, logo reference, favicon reference, reference DOM summary) and MUST NOT accept embedded binary/base64 content.
- **FR-007**: The system MUST store an asset's value exactly as entered (e.g., "akbank.com"), without transforming it to a derived or registrable form.
- **FR-008**: The system MUST prevent registration of a duplicate active asset (same owning entity, asset type, and value) and surface the rejection as a visible message rather than failing abnormally.
- **FR-009**: The system MUST present a server-rendered screen that lists all entities and, under each, its registered assets; an entity with no assets MUST render with an empty asset list rather than an error.
- **FR-010**: The system MUST keep entity/asset registration logic within its own module and MUST NOT introduce reads/writes into unrelated pipeline modules.

### Preserved Behaviors *(MUST stay unchanged)*

- Greenfield slice — there is no prior runtime behavior to preserve. Two non-behavioral invariants MUST hold: (1) conformance to the v5 data-model column contract (`docs/migration_final_schema/migration_final_schema.md`) for `entities` and `assets`; (2) the modular-monolith boundary — entity/asset logic must not leak into, or depend on the internals of, unrelated modules.

### Key Entities *(include if the feature involves data)*

- **Protected Entity**: the brand / institution / person being protected. Key attributes: name, type (free-text classification), creation/update timestamps. It is the owner anchor every downstream record references.
- **Asset**: a protected digital property (a domain) belonging to exactly one entity. Key attributes: owning-entity reference, asset type ("domain"), value (stored as entered), active flag (default true), optional reference metadata (references/summaries only), creation/update timestamps. An optional asset-group reference exists in the data model but is left unused in this slice.

## Scope *(mandatory)*

- **In scope**:
  - Registering a protected entity (name + type) and persisting it.
  - Registering one or more domain assets under an existing entity (value, asset type "domain", active default, optional reference-only metadata).
  - A server-rendered screen listing entities and their assets.
  - The minimal module wiring to host these two capabilities inside the modular monolith.
  - Relying on the already-written asset-layer schema for the tables — this slice authors no new migration and runs none (migrations are applied as a manual human/setup step).
- **Out of scope** (MUST NOT be built or touched):
  - `exclusions` and `asset_groups` management (the asset-group reference stays nullable and unused).
  - `candidate_discoveries`, `candidates`, and everything downstream (DNS/HTTP validation, crawler, feature extraction, similarity, detection signals, risk scoring, review, cases, evidence).
  - `blob_storage` and any binary/logo/favicon upload or storage — metadata holds references only.
  - PGMQ / JobQueue, workers, and any asynchronous pipeline behavior.
  - Multi-tenant isolation, authentication / RBAC, and population of any `created_by`-style fields.
  - Edit / delete / deactivation of entities or assets (this slice is create + view only).
  - Any use of DB-level `enum` types.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a single session, an analyst can register entity "Akbank" and a domain asset "akbank.com" under it and see both on the listing screen — using only the registration forms, with no manual data steps.
- **SC-002**: 100% of invalid submissions (blank entity name; blank asset value; asset under a non-existent entity) are rejected with a visible message and produce zero new persisted records.
- **SC-003**: An entity with no assets is displayed correctly (empty asset list) and never produces an error.
- **SC-004**: A type value other than "brand" (e.g., "institution") is accepted and shown exactly as entered.
- **SC-005**: 100% of registered asset values are stored exactly as entered, with no transformation to a derived/registrable form.
- **SC-006**: A second attempt to register the same active asset (same entity + type + value) is rejected with a visible message and creates no duplicate record.

## Constraints / Imposed Decisions *(if any)*

<!-- Externally imposed decisions (stack, data-model rules, settled project decisions). The behavioral
     requirements above stay free of HOW; the imposed HOW is recorded here for plan.md. -->

- Backend is **Scala Play Framework**; the UI is **server-rendered Twirl** (no React/SPA). *(architecture decision)*
- Database is **PostgreSQL**; the architecture is a **modular monolith** — this capability lives in the `asset` module under `app/drp/asset/`. *(architecture decision)*
- `type` and `asset_type` are stored as **DB strings**, represented in code via a sealed/open enum + a single codec — **not** DB-level enum types. *(data-model design rule)*
- Audit convention: mutable tables (`entities`, `assets`) carry `created_at` + `updated_at`; `updated_at` is maintained by a DB trigger, not by application code. *(data-model rule)*
- `assets.metadata` (JSONB) holds **references/summaries only** (`homepage_url`, `login_page_url`, `logo_ref`, `favicon_ref`, `reference_dom_summary`); binary/base64 content MUST NOT be embedded (that path is `blob_storage`, out of scope here). *(data-model rule)*
- The asset-group reference (`asset_group_id`) is nullable and intentionally unused in this slice.
- **Migrations**: schema is managed as manual, versioned SQL under `app/migrations/drp-postgres/` — **not** Play Evolutions / Flyway — and the application **never** auto-migrates on startup. The asset-layer file (`V001__asset_layer_up.sql`) is already written; this slice authors no migration and executes none (applying it is a manual human/setup step). *(settled project decision — CLAUDE.md §11, constitution "Migration discipline")*
- This slice has **no authentication**; the screen is reachable without login. NOTE: this diverges from the documented intent that DRP screens are protected by the existing pac4j auth (`current_architecture_map.md`); it is an intentional simplification for this foundational slice and should be revisited before later UI slices.

## Assumptions

- The **v5 data-model** — `docs/migration_final_schema/migration_final_schema.md` — is the authoritative source for `entities` / `assets` column names, types, nullability, defaults, and constraints.
- **Mutation surface** is create + view only; edit and deactivation (`is_active` toggle) are deferred to a later slice.
- **Exclusions** ship as a separate follow-up user story, not bundled into this one.
- **Asset entry** is one-at-a-time and repeatable (registering several assets means repeating the action), not a bulk multi-asset form.
- **Duplicate policy** *(clarified 2026-06-26)*: entity names may repeat — no uniqueness constraint on `entities.name` (per v5 schema). Active-asset duplicates (same entity + asset type + value) are blocked at the data layer and surfaced as a visible message (see FR-008).
- A reachable PostgreSQL instance with the asset-layer tables already applied (via the setup/migration scripts) is a precondition; the Play application boots in the current greenfield skeleton.
