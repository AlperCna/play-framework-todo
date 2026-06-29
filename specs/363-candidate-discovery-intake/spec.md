# Feature Specification: Candidate Discovery Intake & Staging

**Feature Branch**: `features/363-Candidate-Discovery`

**Created**: 2026-06-29

**Type**: Feature Implementation

**Status**: Draft

**Input**: User description: "US-363 — Candidate Discovery Intake & Staging"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Manual Suspicious Domain Submission (Priority: P1)

An analyst has spotted a suspicious domain or URL that resembles a protected brand. They open the discovery intake form, select the target protected entity (and optionally one of its domain assets), paste or type the suspicious value, and submit. The system normalises the value, checks it against active exclusions, and stores it in the staging area with its eligibility status clearly visible.

**Why this priority**: This is the primary intake path that all other discovery flows depend on. Without a working manual submission path there is no way to verify that normalisation, deduplication, and exclusion logic behave correctly.

**Independent Test**: Create an entity with at least one active asset and one active exclusion. Submit a valid domain manually. Verify the staging record appears with the correct source, DNS status, and skip-reason. Repeat with a domain that matches the exclusion and confirm a whitelisted record is created instead.

**Acceptance Scenarios**:

1. **Given** an entity exists and an analyst opens the intake form, **When** the analyst selects the entity and submits a valid suspicious domain, **Then** a staging record is created with `source = manual`, `dns_status = pending`, `skip_reason` empty, and both the original and normalised values stored.
2. **Given** a submitted URL contains a scheme, port, path, and query string, **When** the system normalises it, **Then** only the hostname is retained in `normalised_value`; scheme, port, path, query and fragment are discarded.
3. **Given** a submitted hostname has uppercase characters or a trailing DNS dot, **When** normalised, **Then** the stored `normalised_value` is lowercase with no trailing dot; the `www` label is preserved.
4. **Given** an analyst submits a domain that exactly matches an active exclusion for the selected entity, **When** normalisation and exclusion check run, **Then** a staging record is created with `skip_reason = whitelisted` and the record is NOT eligible for later validation.
5. **Given** an analyst submits a value that does not parse as a valid hostname (non-empty malformed input), **When** the system processes it, **Then** a staging record is created with `skip_reason = invalid_format` and a deterministic `invalid:` prefixed `normalised_value`, ensuring the record is NOT eligible for validation.
6. **Given** a staging record already exists for the same entity and normalised value, **When** the same value is submitted again, **Then** no second record is created and the existing record remains unchanged.
7. **Given** an analyst submits a blank input, **When** the form is submitted, **Then** an inline validation error is shown and no staging record is created.
8. **Given** an analyst selects an asset that belongs to a different entity than the selected entity, **When** submitting, **Then** a clear error is shown and no staging record is created.
9. **Given** no asset is selected, **When** a valid domain is submitted, **Then** a staging record is created with `asset_id` empty (no asset association required).

---

### User Story 2 — Permutation Intake for a Domain Asset (Priority: P2)

An analyst wants to discover look-alike domains for a known protected domain asset. They trigger the permutation intake for an active domain asset. The system requests a batch of look-alike values from a replaceable permutation-provider boundary and stages each unique result through the same normalisation, deduplication, and exclusion-check pipeline as manual submissions.

**Why this priority**: Permutation intake multiplies coverage without analyst effort. Because it uses the same staging pipeline as manual intake it proves the intake contract is provider-independent and correctly handles batch de-duplication.

**Independent Test**: Configure the system with a deterministic fake provider that returns a known fixed list of values (some valid, some duplicates, some matching exclusions). Trigger permutation intake for an active domain asset. Verify that unique valid results are staged as `source = permutation`, duplicates are silently ignored, exclusion matches are staged as whitelisted, and the provider's identity is not exposed in the staging records.

**Acceptance Scenarios**:

1. **Given** an active domain asset exists, **When** permutation intake is triggered, **Then** each unique look-alike value returned by the provider is staged with `source = permutation` and the source asset id; values already staged under the same entity are silently skipped.
2. **Given** a provider result matches an active exclusion for the entity, **When** staged, **Then** the result receives `skip_reason = whitelisted`.
3. **Given** a provider value is non-empty but malformed, **When** staged, **Then** it receives `skip_reason = invalid_format` and the deterministic `invalid:` normalised-value fallback, identical to malformed manual input.
4. **Given** the provider returns an empty list, **When** intake completes, **Then** no staging records are created and no error is raised.
5. **Given** the provider call fails, **When** intake attempts to proceed, **Then** a clear failure is returned and no partial batch of staging records is written.
6. **Given** permutation intake is requested for an inactive asset, an unknown asset, or a non-domain asset, **When** the request is processed, **Then** a clear error is returned and no staging records are created.

---

### User Story 3 — Discovery List and Detail View (Priority: P3)

An analyst wants to review what is already in the staging area. They open the discovery list for a given entity and can see each entry's value, normalised value, source, DNS status, and skip reason. They can drill into a single entry to see its full detail.

**Why this priority**: Visibility into the staging area is essential for verifying intake outcomes manually and for confirming that exclusion and deduplication logic behaved as intended.

**Independent Test**: After submitting several discoveries (valid, whitelisted, invalid) for an entity, open the list view. Confirm all three types are visible with clearly distinct status labels. Navigate to the detail view of each and confirm all fields are present.

**Acceptance Scenarios**:

1. **Given** staging records exist for an entity, **When** the analyst views the discovery list, **Then** each record shows at minimum: value, normalised value, source, DNS status, and skip reason (or a clear "pending validation" label when skip reason is absent).
2. **Given** a mix of pending, whitelisted, and invalid records exists, **When** viewing the list, **Then** each category is clearly distinguishable at a glance.
3. **Given** the analyst selects a staging record, **When** the detail view loads, **Then** it shows the entity, optional asset, original value, normalised value, source, DNS status, and skip reason.

---

### Edge Cases

- A normalised value that is identical across two different entities is stored as two independent records (no cross-entity uniqueness constraint).
- The same malformed value submitted twice under the same entity produces the same `invalid:` normalised form; the uniqueness guard fires and no second record is created.
- One invalid or whitelisted submission has no effect on the entity's or asset's existing data.
- Permutation intake that returns duplicate values within the same provider response stages each unique value once and silently discards duplicates — this is not reported as an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an intake form through which an analyst selects an existing protected entity, enters a suspicious domain or URL, and optionally selects an asset belonging to that entity.
- **FR-002**: The system MUST normalise every submitted value by extracting the hostname (when a URL is provided), converting IDN hostnames to ASCII/Punycode, lowercasing, removing a trailing DNS dot, and preserving the `www` label. Scheme, credentials, port, path, query, and fragment MUST be discarded.
- **FR-003**: The system MUST assign a deterministic `invalid:` prefixed normalised value to any non-empty value that cannot be parsed as a valid hostname, ensuring traceability without blocking processing.
- **FR-004**: The system MUST enforce that the combination of entity and normalised value is unique in the staging area — a submission that produces an already-existing normalised value for the same entity MUST NOT create a second staging record.
- **FR-005**: The system MUST evaluate active exclusions for the selected entity before a staging record is marked eligible for validation. Exclusion matching MUST support four modes: exact hostname match, same registrable domain, strict subdomain of the exclusion hostname, and glob-based full-hostname pattern.
- **FR-006**: The system MUST retain an exclusion-matched discovery as a staging record with skip reason `whitelisted` so that the match is auditable.
- **FR-007**: The system MUST retain a non-empty malformed submission as a staging record with skip reason `invalid_format` so that problematic inputs are traceable.
- **FR-008**: A valid, non-excluded discovery MUST be stored with DNS status `pending`, skip reason absent, zero failed-check count, and no HTTP/check result — making it eligible for the later validation step.
- **FR-009**: The system MUST expose a provider boundary for requesting look-alike domain values so that the intake behavior is independent of the underlying generation algorithm.
- **FR-010**: The system MUST support a deterministic fake provider that returns a known fixed batch of look-alike values, enabling local verification and automated testing without a real permutation tool.
- **FR-011**: Each value returned by the provider MUST pass through the same normalisation, deduplication, and exclusion-check pipeline as manual intake, and be staged with `source = permutation` and the source asset id.
- **FR-012**: Duplicate values within a permutation batch (same normalised value already staged under the entity) MUST be silently skipped without failing the rest of the batch.
- **FR-013**: A provider failure MUST result in a clear error and MUST NOT write a partial staging batch.
- **FR-014**: Permutation intake MUST be rejected with a clear error if the target asset is unknown, inactive, or not of domain type.
- **FR-015**: The discovery read surface MUST obtain entity, asset, and exclusion data through the asset module's public read interface; it MUST NOT query asset-owned tables directly.
- **FR-016**: The system MUST provide a list view showing all staging records for a given entity, with each record's value, normalised value, source, DNS status, and skip reason clearly visible. The list MUST be filterable by status category (pending / whitelisted / invalid_format); filtering by source is out of scope for this story.
- **FR-017**: The system MUST provide a detail view for a single staging record showing entity, optional asset association, original value, normalised value, source, DNS status, and skip reason.
- **FR-018**: Blank input MUST be rejected with an inline validation error before any staging record is attempted.
- **FR-019**: An asset selected during manual intake MUST belong to the selected entity; cross-entity asset selection MUST be rejected with a clear error.

### Preserved Behaviors *(MUST stay unchanged)*

- Protected Entity Setup behavior (entity, asset, asset group, exclusion CRUD) remains unchanged.
- The asset module remains the sole writer for entity, asset, asset-group, and exclusion data.
- No row is ever written directly to the `candidates` table by this feature.
- Existing ScalaTest suites continue to pass after all changes.

### Key Entities *(include if the feature involves data)*

- **Staging Discovery** (`candidate_discoveries`): represents one candidate suspicious value at the intake boundary. Carries original submitted value, normalised value, source (`manual` or `permutation`), optional entity-owned asset reference, DNS status (defaults to `pending`), skip reason (null = eligible, `whitelisted` = exclusion match, `invalid_format` = malformed), failed-check count, and created/updated timestamps. Uniqueness is enforced on entity + normalised value.
- **Exclusion** (read from asset module via read port): an active entity-scoped pattern that defines domains the system should never flag. Has a match type (`exact`, `registrable_domain`, `subdomain_of`, `pattern`) and a value. The discovery module reads exclusions but never writes them.
- **Asset View** (read-model from asset module): a projection of an asset record exposing at minimum its identifier, entity association, asset type, and active flag. The discovery module uses this to validate asset ownership and eligibility for permutation intake.
- **Permutation Provider** (provider boundary): a replaceable component that accepts an active domain hostname and returns a list of look-alike candidate strings. The intake behavior is defined by the contract, not by any specific algorithm.

## Scope *(mandatory)*

- **In scope**:
  - `app/drp/discovery/` module — all layers: domain, application, application/ports, infrastructure, web.
  - `candidate_discoveries` table (read + write); no other table is written.
  - A Twirl intake form and list/detail views under the discovery module's web layer.
  - A new application-level read port added to the asset module exposing `AssetView` with an `isActive` flag (read-only addition; no asset write behavior changes).
  - A replaceable permutation-provider boundary (trait/interface) and a deterministic fake implementation.
  - Routing entries for the new discovery endpoints.
  - ScalaTest unit specs for the intake service and an in-memory discovery repository adapter.

- **Out of scope** (MUST NOT be touched):
  - DNS/HTTP validation, candidate promotion, and writing to `candidates`.
  - PGMQ, `JobQueue`, workers, and any background scheduling.
  - Automatic retry or recheck behavior.
  - Production dnstwist integration and process execution.
  - Permutation algorithms, variant-family selection, homoglyph/IDN generation, or permutation quality tuning.
  - CT Log, WHOIS, complaint, or external-feed integrations.
  - Crawl handoff or `crawl_queue`.
  - Crawling, HTML, DOM, screenshot, OCR, or binary collection.
  - Similarity, scoring, review, case, and evidence processing.
  - Changes to entity, asset, asset-group, or exclusion records.
  - Global or entity-less exclusion management.
  - Authentication, RBAC, or multi-tenant isolation.
  - Hard delete, soft delete, or deactivation flows.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An analyst can submit a suspicious domain and see the resulting staging record — with original value, normalised value, source, and eligibility status — within the same page response.
- **SC-002**: A valid submission that matches no exclusion is visible in the list view as `pending` and is not duplicated when the same normalised value is resubmitted under the same entity.
- **SC-003**: All four exclusion match types (`exact`, `registrable_domain`, `subdomain_of`, `pattern`) correctly classify matching domains as whitelisted in automated tests with zero false positives among the provided non-matching cases.
- **SC-004**: Permutation intake using the deterministic fake provider stages the expected unique valid results and correctly ignores duplicates and exclusion-matched values — verified by an automated test that can be reproduced deterministically.
- **SC-005**: A provider failure leaves the staging area in the state it was before the request — no partial batch is observable.
- **SC-006**: All existing Protected Entity Setup tests continue to pass without modification.
- **SC-007**: The discovery module contains no direct queries against asset-module-owned tables; all cross-module data access is through the asset module's read port — verifiable by code review of import statements and dependency graph.

## Constraints / Imposed Decisions

- Server-rendered Twirl views; no React or client-side SPA is introduced.
- The intake form and list/detail views use the modular monolith's established controller → service → repository pattern.
- The discovery module is the sole writer for `candidate_discoveries`; the asset module remains the sole writer for its tables.
- Cross-module reads (entity, asset, exclusion data) MUST go through the asset module's `application/ports/` read interface returning typed read-models — no direct Slick queries against asset-owned tables.
- Registrable-domain matching MUST use a public-suffix-aware domain parsing library; naive last-two-label comparison is not acceptable.
- No PostgreSQL ENUM is introduced; `dns_status` and `skip_reason` use `TEXT + CHECK` constraints consistent with the existing schema conventions.
- All foreign keys remain `ON DELETE RESTRICT`.
- Any schema change (if needed beyond the existing `candidate_discoveries` table) is a hand-written, versioned SQL migration file — the application never auto-migrates.
- The permutation provider boundary is defined as an interface/trait; the fake implementation is the only one required by this story; the future dnstwist adapter is explicitly out of scope.
- Large or crawler-derived content MUST NOT be stored in staging discovery records.

## Assumptions

- `candidate_discoveries` already exists in the database with the schema defined in `docs/migration_final_schema/migration_final_schema.md` (V001–V006 migrations applied); no structural migration to that table is required for this feature.
- At least one entity and one active domain asset exist in the local environment for manual verification (per the demo seed data).
- The asset module's existing read-seam is extended to expose `isActive` on `AssetView`; this is a non-breaking additive change that does not require any existing asset module consumer to be updated.
- Exclusion evaluation uses the entity-scoped exclusions already stored by Protected Entity Setup; no new exclusion management UI is introduced.
- The discovery list defaults to showing all staging records for a given entity in reverse-chronological order, paginated per the project's standard `Page`/`PageRequest` convention (growing set — pagination is the default per Constitution IV).
- Manual intake form pre-populates the entity selector; permutation intake is triggered via a button/link on the existing asset detail page that navigates to a discovery module form pre-filled with that asset's entity and asset id — the analyst does not re-select the asset, and no asset-selection UI is needed inside the discovery module for permutation intake.

## Clarifications

### Session 2026-06-29

- Q: Should the analyst be able to filter the discovery list by status category (pending/whitelisted/invalid) or by source (manual/permutation)? → A: Status filter only (pending / whitelisted / invalid_format); source filter is out of scope for this story.
- Q: How does the analyst trigger permutation intake — from the asset detail page or a standalone discovery module page? → A: Button/link on the asset detail page navigates to a discovery module form pre-filled with the asset's entity and asset id; no asset-selection UI inside the discovery module.
