# Feature Specification: Exclusion (Allowlist) Registration

**Feature Branch**: `002-exclusion-registration`

**Created**: 2026-06-26

**Type**: Feature Implementation

**Status**: Draft

**Input**: User description: "US-002-Exclusion-Registration.md dosyasındaki hazır user story'yi feature girdisi olarak kullan." (foundational vertical slice — register and view exclusions / allowlist entries under a protected entity, on a server-rendered screen; completes the entity + asset + exclusion setup surface)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register and view exclusions under a protected entity (Priority: P1)

An analyst declares *what the platform must never flag* by registering an exclusion (an allowlist entry — an owned-but-unmonitored or legitimate third-party domain/URL) under an existing protected entity. The analyst provides the exclusion's value, a match type, and a reason; the entry is stored as active and immediately appears on a server-rendered listing screen for that entity. This completes the foundational setup surface (entity + asset + exclusion) so the future discovery pipeline has a real, queryable allowlist to consult instead of a placeholder or hard-coded list.

**Why this priority**: The entity + asset layer already exists (US-001); exclusions are the missing third foundational piece. Without a place to declare "this domain is ours / legitimate — never flag it," the future discovery stage would produce false positives or rely on a hard-coded allowlist. Registering and viewing exclusions is a viable, demoable slice on its own — it proves the form → persistence → server-rendered page path for a new capability hosted inside the existing asset module.

**Independent Test**: With an entity (e.g., "Akbank") present, register an exclusion `"akbankdirekt.com"` (match type "exact", reason "owned_unmonitored") under it; confirm it persists under that entity with the correct owner reference, the entered value/match type/reason, active status true, the default creator stamp, and creation/update timestamps, and that it appears under the entity on the listing screen. No downstream pipeline is required for this test to pass.

**Acceptance Scenarios**:

1. **Given** an existing entity, **When** the analyst registers an exclusion with value "akbankdirekt.com", match type "exact", and reason "owned_unmonitored", **Then** an exclusion is stored under that entity with active status true, the default creator stamp, and creation/update timestamps, and it appears on the entity's exclusion listing screen.
2. **Given** the exclusion registration form, **When** the analyst submits a blank value, **Then** the system shows a visible validation message and persists nothing.
3. **Given** no entity with the referenced id exists, **When** the analyst attempts to register an exclusion under it, **Then** the system rejects the attempt and persists nothing.
4. **Given** an existing entity, **When** the analyst registers an exclusion with a match type from the allowed set other than "exact" (i.e., "registrable_domain", "subdomain_of", or "pattern"), **Then** the exclusion is accepted and stored with that match type.
5. **Given** an existing entity, **When** the analyst submits a match type outside the allowed set, **Then** the system rejects the attempt and persists nothing.
6. **Given** an existing entity, **When** the analyst registers an exclusion with a reason such as "third_party_legit" (an open classification), **Then** the exclusion is accepted and the reason is stored exactly as entered.
7. **Given** an active exclusion already exists for an entity with a given value and match type, **When** the analyst registers another active exclusion with the same entity, value, and match type, **Then** the system rejects the duplicate with a visible message and creates no second record.
8. **Given** an entity with no exclusions, **When** the analyst opens that entity's exclusion listing screen, **Then** the screen renders an empty list rather than an error.

---

### Edge Cases

- **Blank value**: rejected with a visible validation message; nothing persisted.
- **Exclusion under a non-existent entity**: rejected; nothing persisted (the owning entity must exist).
- **Match type outside the allowed set** (anything other than `exact` / `registrable_domain` / `subdomain_of` / `pattern`): rejected; nothing persisted. Match type is a closed set.
- **Open reason value** (e.g., "third_party_legit", or any other non-blank reason): accepted and stored exactly as entered — reason is an open classification with no fixed set.
- **Active duplicate** (same owning entity + value + match type while active): rejected with a visible message; the system does not crash. (Active-exclusion uniqueness is enforced at the data layer.)
- **Entity with zero exclusions**: valid — the screen renders an empty exclusion list, not an error.
- **`match_type = "pattern"` with a glob/regex value**: the value is stored verbatim with no registration-time pattern validation; interpreting/applying the pattern is downstream and out of scope here.
- **Non-domain-looking value** (e.g., contains spaces or has no dot): accepted and stored as entered — only a blank value is rejected.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an analyst to register an exclusion under an existing protected entity by providing a value, a match type, and a reason, persisting it with active status defaulting to true, the default creator stamp, and creation/update timestamps.
- **FR-002**: The system MUST reject exclusion registration when the value is blank, persisting nothing and showing a visible validation message.
- **FR-003**: The system MUST reject exclusion registration when the referenced owning entity does not exist, persisting nothing and showing a visible message. The owning entity is required for every exclusion in this slice.
- **FR-004**: The system MUST accept a match type only from the closed set `exact`, `registrable_domain`, `subdomain_of`, `pattern`, and MUST reject any value outside that set, persisting nothing.
- **FR-005**: The system MUST accept any non-blank reason as an open classification (e.g., "manual", "owned_unmonitored", "third_party_legit") without enforcing a fixed set of allowed values, and MUST store it exactly as entered.
- **FR-006**: The system MUST prevent registration of a duplicate active exclusion (same owning entity, value, and match type) and surface the rejection as a visible message rather than failing abnormally.
- **FR-007**: The system MUST store an exclusion's value exactly as entered (e.g., "akbankdirekt.com"), without transforming it to a derived, normalized, or registrable form.
- **FR-008**: The system MUST present a server-rendered screen that lists the exclusions registered for an entity; an entity with no exclusions MUST render with an empty list rather than an error.
- **FR-009**: The system MUST host the exclusion capability inside the existing asset module, reusing the existing entity-existence check rather than duplicating it, and MUST NOT alter the US-001 entity/asset behavior or introduce reads/writes into unrelated pipeline modules.

### Preserved Behaviors *(MUST stay unchanged)*

- The US-001 entity/asset registration behavior MUST NOT be altered; this slice reuses the existing entity-existence lookup rather than duplicating or modifying it. Two non-behavioral invariants MUST also hold: (1) conformance to the v5 data-model column contract (`docs/migration_final_schema/migration_final_schema.md`) for `exclusions`; (2) the modular-monolith boundary — exclusion logic belongs to the asset module (which already owns `entities`/`assets`) and must not leak into, or depend on the internals of, unrelated modules.

### Key Entities *(include if the feature involves data)*

- **Exclusion**: an allowlist entry declaring a domain/URL the platform must never flag, belonging to exactly one protected entity in this slice. Key attributes: owning-entity reference (required here), value (stored as entered), match type (closed set: `exact` / `registrable_domain` / `subdomain_of` / `pattern`), reason (open classification, stored as entered), active flag (default true), creator stamp (defaulted, no current-user source yet), creation/update timestamps. The owning-entity reference is nullable in the data model (reserved for future global exclusions) but required in this slice.

## Scope *(mandatory)*

- **In scope**:
  - Registering an exclusion under an existing entity (value + match type + reason), persisting it with active status defaulting to true and audit stamps.
  - A server-rendered screen listing the exclusions for an entity.
  - The minimal module wiring to host the exclusion capability inside the existing asset module (`app/drp/asset/`), reusing US-001's entity-existence check.
  - Relying on the already-written asset-layer schema (`V001`, which includes `exclusions`) for the table — this slice authors no new migration and runs none (migrations are applied as a manual human/setup step).
- **Out of scope** (MUST NOT be built or touched):
  - **Applying** exclusions during candidate discovery / matching (the `skip_reason = "whitelisted"` skip logic belongs to the `candidate_discoveries` user story) — this slice only registers and views them.
  - Global exclusions (owning entity null) — the owning entity is required here; the column stays nullable for future use.
  - Edit / delete / deactivation of exclusions (this slice is create + view only).
  - Real authentication / RBAC and any real current-user source for the creator stamp.
  - `candidate_discoveries`, `candidates`, and everything downstream; PGMQ / JobQueue; `blob_storage`.
  - The US-001 `entities` / `assets` capability — reused, not modified.
  - Any use of DB-level `enum` types.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a single session, an analyst can register an exclusion "akbankdirekt.com" (match type "exact", reason "owned_unmonitored") under an existing entity and see it on that entity's exclusion listing screen — using only the registration form, with no manual data steps.
- **SC-002**: 100% of invalid submissions (blank value; exclusion under a non-existent entity; match type outside the allowed set; active duplicate) are rejected with a visible message and produce zero new persisted records.
- **SC-003**: An entity with no exclusions is displayed correctly (empty list) and never produces an error.
- **SC-004**: A reason value other than the common ones (e.g., "third_party_legit") is accepted and shown exactly as entered.
- **SC-005**: 100% of registered exclusion values are stored exactly as entered, with no transformation to a derived/normalized/registrable form.
- **SC-006**: A second attempt to register the same active exclusion (same entity + value + match type) is rejected with a visible message and creates no duplicate record.

## Constraints / Imposed Decisions *(if any)*

<!-- Externally imposed decisions (stack, data-model rules, settled project decisions). The behavioral
     requirements above stay free of HOW; the imposed HOW is recorded here for plan.md. -->

- Backend is **Scala Play Framework**; the UI is **server-rendered Twirl** (no React/SPA). *(architecture decision)*
- Database is **PostgreSQL**; the architecture is a **modular monolith** — this capability lives in the existing `asset` module under `app/drp/asset/` (which already owns `entities`/`assets`). *(architecture decision)*
- `match_type` and `reason` are stored as **DB strings**, represented in code via an enum + a single codec — **not** DB-level enum types. `match_type` is a **closed set** backed by a DB CHECK (`exact` / `registrable_domain` / `subdomain_of` / `pattern`) → a sealed enum; `reason` is **open** (no CHECK) → an open enum. *(data-model design rule)*
- Audit convention: mutable tables (`exclusions`) carry `created_at` + `updated_at`; `updated_at` is maintained by a DB trigger, not by application code. `created_by` is **NOT NULL with DB default `'system'`** — there is no current-user seam yet, so this slice relies on that default; it is not nullable and no real auth is wired. *(data-model rule)*
- The owning entity reference (`entity_id`) is nullable in the schema (reserved for future global exclusions) but **required** in this slice. *(scope decision)*
- The active-duplicate guard `(entity_id, value, match_type) WHERE is_active = true AND entity_id IS NOT NULL` is enforced at the data layer; the service/UI surfaces a rejected duplicate as a visible message. *(data-model rule)*
- **Migrations**: schema is managed as manual, versioned SQL under `app/migrations/drp-postgres/` — **not** Play Evolutions / Flyway — and the application **never** auto-migrates on startup. The asset-layer file (`V001__asset_layer_up.sql`) already creates `exclusions`; this slice authors no migration and executes none (applying it is a manual human/setup step). *(settled project decision — CLAUDE.md §11, constitution "Migration discipline")*
- This slice has **no authentication**; the screen is reachable without login. NOTE: this diverges from the documented intent that DRP screens are protected by the existing pac4j auth (`current_architecture_map.md`); it is an intentional simplification carried over from US-001 and should be revisited before later UI slices.

## Assumptions

- The **v5 data-model** — `docs/migration_final_schema/migration_final_schema.md` — is the authoritative source for `exclusions` column names, types, nullability, defaults, and constraints (incl. the `match_type` CHECK, the active-duplicate unique index, and the `created_by` default).
- US-001's `entities` (and `assets`) exist and a reachable PostgreSQL instance with the asset-layer tables already applied (via the setup/migration scripts) is a precondition; the Play application boots in the current greenfield skeleton.
- **Mutation surface** is create + view only; edit and deactivation (active-flag toggle) are deferred to a later slice.
- **Global exclusions** (owning entity null) are deferred; the owning entity is required in this slice while the column stays nullable for future use.
- **Creator stamp** relies on the schema's `'system'` default until an authentication / current-user seam exists; no real auth is wired in this slice.
- **Value and match type** are required inputs alongside reason; the value is stored verbatim (no normalization), and for `match_type = "pattern"` the value is stored without any registration-time pattern validation (interpreting/applying it is downstream).
- **Exclusion entry** is one-at-a-time and repeatable (registering several exclusions means repeating the action), not a bulk multi-exclusion form.
- **Duplicate policy**: active exclusions are unique per `(entity_id, value, match_type)` (enforced at the data layer per the v5 schema); a duplicate active registration is rejected and surfaced as a visible message (see FR-006).
