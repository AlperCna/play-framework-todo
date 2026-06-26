# Phase 0 Research: Exclusion (Allowlist) Registration

All open technical questions resolved before design. No `NEEDS CLARIFICATION` remain — the two
functional ambiguities were settled in the spec's Clarifications (Session 2026-06-26: dedicated
per-entity screen; `reason` required). The items below are the technical/infrastructure decisions for
hosting exclusions inside the existing asset module. The big infrastructure choices (dedicated `drp`
connection, `slick-pg`, `MonaPgProfile`, no-todo-reuse) were already made and **implemented** in US-001;
they are reused here, not re-decided.

---

## D1 — Host exclusions inside the existing `app/drp/asset/` module (additive)

- **Decision**: Add exclusion files to `app/drp/asset/` rather than create a new module. Reuse the
  existing `slick.dbs.drp` connection, `MonaPgProfile`, and Guice `AssetModule` (extend its `configure`
  with two new bindings).
- **Rationale**: The v5 model and CLAUDE.md §3 map `entities, asset_groups, assets, exclusions` to the
  **asset** module. Single-writer: the module that owns `entities` owns `exclusions`. Constitution I
  forbids a parallel mechanism — there is already a working asset module to extend.
- **Alternatives considered**: A separate `exclusion` module — rejected: not in the bounded-context map,
  would duplicate the entity-existence read and the DB wiring, and split a single bounded context.

## D2 — Owning-entity existence via the same-module `EntityRepository.existsById`

- **Decision**: `ExclusionServiceImpl` injects the existing `EntityRepository` port and calls
  `existsById(entityId)` before persisting; missing entity → `AssetDomainError.UnknownEntity(entityId)`
  (the existing error case, reused). The DB FK `exclusions.entity_id → entities(id)` is the backstop.
- **Rationale**: Reuses the established check (FR-009 says reuse, don't duplicate). `EntityServiceImpl`
  already injects `EntityRepository`; this is same-module, port-based — not a cross-module dependency, so
  Constitution I is satisfied. No new method is needed on the entity layer, so US-001 stays untouched.
- **Alternatives considered**: (a) Inject `EntityService` — rejected: it exposes only `register`/`list`,
  not an existence check. (b) Rely on the FK alone and map the SQL violation — rejected as the *primary*
  path: a pre-check yields the friendly message; the FK remains the backstop.

## D3 — `match_type` as a sealed ADT + codec; `reason` as open non-blank `String`

- **Decision**: `ExclusionMatchType` is a sealed trait with `Exact` / `RegistrableDomain` /
  `SubdomainOf` / `Pattern` plus a single string codec (`asValue` / `fromValue`), matching the DB
  `CHECK (match_type IN ('exact','registrable_domain','subdomain_of','pattern'))`. `reason` is a plain
  non-blank `String` stored as entered (no fixed set).
- **Rationale**: Directly mirrors US-001's D4 (`asset_type` sealed ADT + codec; `entities.type`
  free-text `String`) and the constitution's "TEXT + CHECK on closed lifecycle fields → code-side sealed
  enum + codec; open fields → open text". `match_type` is DB-constrained (closed); `reason` is
  intentionally open (CLAUDE.md §4, v5 §6.4 "reason için DB CHECK kullanılmayacaktır").
- **Alternatives considered**: PG enum — forbidden by constitution. Free-text `match_type` — rejected:
  the DB CHECK constrains it to the four values. A sealed ADT for `reason` — rejected: reason is open by
  design; an open enum/wrapper adds nothing over a validated `String` here.

## D4 — Closed-set validation lives in the domain smart constructor

- **Decision**: `Exclusion.create(entityId, value, matchTypeRaw, reasonRaw)` validates in the domain:
  non-blank `value` → else `BlankExclusionValue`; non-blank `reason` → else `BlankExclusionReason`;
  `ExclusionMatchType.fromValue(matchTypeRaw)` → `InvalidMatchType(raw)` if outside the four. The web
  form enforces presence (`nonEmptyText`); the domain is the authoritative validator.
- **Rationale**: Keeps closed-set + blank validation pure and unit-testable without Play/Slick
  (Constitution I), and keeps the rejection a typed `AssetDomainError` surfaced as a visible message.
- **Alternatives considered**: Validate `match_type` only at the DB CHECK — rejected: the spec requires a
  visible rejection with nothing persisted (FR-004); a pre-insert domain check gives that and avoids a
  round-trip. Validate in the controller — rejected: leaks domain rules into `web`.

## D5 — Dedicated per-entity screen by URL; US-001 list view stays untouched (clarification reconciliation)

- **Decision**: The exclusions screen is a dedicated route scoped to one entity —
  `GET /drp/assets/entities/:entityId/exclusions` — rendered by a **new** `exclusions.scala.html`. It is
  reachable by its entity-scoped URL. A navigation link from the `/drp/assets` entity list is **deferred**
  to a later slice, because the clarification answer (Session 2026-06-26) also fixed "the US-001 view
  stays untouched" and FR-008 forbids altering that view.
- **Rationale**: Reconciles the two halves of the same clarification answer: "reached from the entity
  list" is satisfied structurally (the route is nested under the entity) without editing the protected
  US-001 view; adding the actual hyperlink would modify `list.scala.html`, which FR-008 / Scope put
  off-limits. The header shows `Exclusions for entity #<id>` (guarded by `existsById`); displaying the
  entity *name* would need a new entity read method (US-001 change) and is therefore also deferred.
- **Alternatives considered**: (a) Add a per-row link in `list.scala.html` — rejected: violates the
  "view untouched" constraint. (b) Nest exclusions into the `/drp/assets` page — rejected: this is
  clarification option B, not the chosen A. (c) Add `EntityRepository.findById` to show the entity name —
  deferred: additive but touches US-001; not needed for the slice to pass.

## D6 — Duplicate handling: service pre-check (UX) + DB partial unique index (backstop)

- **Decision**: `ExclusionServiceImpl` calls `repo.existsActiveDuplicate(entityId, value, matchType)`
  before insert; if true → `AssetDomainError.DuplicateActiveExclusion(...)` (visible message, nothing
  persisted). The V001 partial unique index `uq_exclusions_entity_active_value_match`
  (`WHERE is_active = true AND entity_id IS NOT NULL`) is the authoritative race backstop.
- **Rationale**: Mirrors US-001's asset duplicate approach. One bulk-style existence query (no loop) for
  the friendly message; the unique index guarantees correctness under concurrency (Constitution V —
  read/write paths address races; the DB is the source of truth, not the pre-check).
- **Alternatives considered**: Pre-check only (no index reliance) — rejected: races could double-insert.
  Index only, map the `SQLException` — acceptable hardening but a poorer first-line UX; the pre-check is
  primary and the index is the backstop. Catching the unique violation as additional hardening is
  optional and may be added later.

## D7 — `entity_id` modeled as `Option[Long]` in the Slick row; required (`Long`) in the domain

- **Decision**: The `exclusions.entity_id` column is nullable in V001 (reserved for future global
  exclusions), so the Slick `ExclusionRow` types it as `Option[Long]` to match the DB contract. The
  domain `Exclusion.entityId` is a required `Long`; the mapper writes `Some(entityId)` and, on read in
  this slice, expects `Some` (rows are always entity-scoped here; `listByEntity` filters by `entity_id`).
- **Rationale**: Faithfulness to the column contract (data-model rule) without leaking the
  not-yet-supported global-exclusion case into the domain. Global exclusions (`entity_id` null) are
  explicitly out of scope.
- **Alternatives considered**: Type the column `Long` (NOT NULL) in Slick — rejected: misrepresents the
  nullable DB column and would break the future global-exclusion slice's mapping.

## D8 — Insert omits DB-managed columns (`is_active`, `created_by`, timestamps)

- **Decision**: The Slick insert writes only `entity_id`, `value`, `match_type`, `reason`. `is_active`
  (DEFAULT true), `created_by` (DEFAULT `'system'`), `created_at`/`updated_at` (DEFAULT `now()` + trigger)
  are left to the DB. Row mappers read all columns back; the app never writes `updated_at`.
- **Rationale**: Matches US-001's D6 and `SlickEntityRepository` (insert-subset pattern). There is no
  current-user seam, so the `'system'` default is the intended source for `created_by` (spec Constraints).
- **Alternatives considered**: App-supplied `is_active`/`created_by`/timestamps — rejected: redundant
  with (and potentially inconsistent with) the DB defaults/trigger.

## D9 — Testing: ScalaTest + in-memory repository adapters

- **Decision**: `ExclusionSpec` (domain) covers the smart-constructor validation; `ExclusionServiceSpec`
  (application) covers register/list using `InMemoryExclusionRepository` + `InMemoryEntityRepository`.
  The in-memory exclusion adapter enforces the active-duplicate guard so the duplicate rejection is
  testable without a live DB. The PostgreSQL/Slick path is validated manually via `quickstart.md`.
- **Rationale**: Matches the constitution's Dev-Workflow rule and the existing
  `EntityServiceSpec`/`EntitySpec` pattern (in-memory backend → DB-less service tests). Existing suites
  must stay green.
- **Alternatives considered**: Testcontainers against PostgreSQL — deferred (v1 rigor is human review +
  quickstart; integration tests can come later).

---

### Inputs that needed NO research (already fixed by spec/constitution/US-001)

- Stack (Scala/Play/Twirl/PostgreSQL/Guice), no-React, manual migrations, module layering, ON DELETE
  RESTRICT, no PG enum — imposed decisions in the spec's Constraints and the constitution.
- `slick.dbs.drp` connection, `slick-pg`/PostgreSQL driver, `MonaPgProfile`, no-todo-scaffold-reuse —
  decided in US-001 (research D1–D3) and **already implemented**; reused as-is.
- Listing-screen placement and blank-`reason` handling — settled in the spec's Clarifications
  (Session 2026-06-26).
