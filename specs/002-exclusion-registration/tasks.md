---
description: "Task list for Exclusion (Allowlist) Registration"
---

# Tasks: Exclusion (Allowlist) Registration

**Input**: Design documents from `specs/002-exclusion-registration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/exclusion-routes.md, quickstart.md

**Tests**: ScalaTest specs ARE included — required by research.md (D9) and the constitution's Dev-Workflow rule (suites must stay green; in-memory repos enable DB-less service tests).

**Organization**: One user story (US1 = register + view per-entity exclusions, P1). This slice is **additive within the existing `app/drp/asset/` module**; the DRP DB connection, `slick-pg` deps, `MonaPgProfile`, and `AssetModule` registration already exist (from US-001) and are reused, not re-created.

## Format: `[ID] [P?] [Story] Description with file path`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 (setup, foundational, polish carry no story label)

## Path Conventions

Modular monolith: feature code under `app/drp/asset/`, tests under `test/drp/asset/`, routes in `conf/routes`. No `build.sbt` / `conf/application.conf` / migration changes in this slice (already wired).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the inherited wiring this slice depends on — **no changes** are made here (all present from US-001).

- [ ] T001 Verify prerequisites exist and require no change: `slick-pg` + PostgreSQL driver in `build.sbt`; the `slick.dbs.drp` block + `play.modules.enabled += "drp.asset.application.AssetModule"` in `conf/application.conf`; `app/drp/shared/infrastructure/MonaPgProfile.scala`; and the `exclusions` table (CHECK `ck_exclusions_match_type`, FK `fk_exclusions_entity`, partial unique index `uq_exclusions_entity_active_value_match`, `set_updated_at` trigger) in `app/migrations/drp-postgres/V001__asset_layer_up.sql`

**Checkpoint**: `sbt compile` succeeds on the current tree; the `drp` datasource and `exclusions` schema are in place.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend the module-wide error ADT both the domain and the tests depend on. ⚠️ Complete before US1.

- [ ] T002 Extend the shared `AssetDomainError` ADT in `app/drp/asset/domain/AssetDomainError.scala` with additive cases `BlankExclusionValue`, `BlankExclusionReason`, `InvalidMatchType(value: String)`, and `DuplicateActiveExclusion(entityId: Long, value: String, matchType: String)` (reuse the existing `UnknownEntity` for FR-003; do not modify existing entity/asset cases)

**Checkpoint**: Foundation ready — US1 can begin.

---

## Phase 3: User Story 1 - Register and view exclusions under a protected entity (Priority: P1) 🎯 MVP

**Goal**: An analyst registers an exclusion (value + match type + reason) under an existing entity and sees it on a dedicated per-entity exclusions screen.

**Independent Test**: With an entity present, register "akbankdirekt.com" / `exact` / "owned_unmonitored" → it persists (active, `created_by = system`, timestamps) and appears at `GET /drp/assets/entities/:id/exclusions`. Blank value, blank reason, unknown entity, out-of-set match type, and an active duplicate are each rejected with a visible message and persist nothing; an entity with no exclusions renders an empty list. (quickstart scenarios 1–10)

### Tests for User Story 1

- [ ] T003 [P] [US1] Domain spec for the `Exclusion` smart constructor (blank value → `BlankExclusionValue`; blank reason → `BlankExclusionReason`; out-of-set match type → `InvalidMatchType`; each of the four allowed match types accepted; `value` kept exactly as entered) in `test/drp/asset/domain/ExclusionSpec.scala`
- [ ] T004 [P] [US1] Service spec for `ExclusionService` via `InMemoryExclusionRepository` + `InMemoryEntityRepository` (register under an existing entity persists + appears in `listByEntity`; blank value, blank reason, unknown entity, and an active duplicate each → the matching error with nothing stored; open reason e.g. "third_party_legit" accepted; `listByEntity` returns only the target entity's exclusions) in `test/drp/asset/application/ExclusionServiceSpec.scala`

### Implementation for User Story 1

- [ ] T005 [P] [US1] Create `ExclusionMatchType` sealed ADT + string codec (`Exact`↔"exact", `RegistrableDomain`↔"registrable_domain", `SubdomainOf`↔"subdomain_of", `Pattern`↔"pattern"; `asValue` / `fromValue`) in `app/drp/asset/domain/ExclusionMatchType.scala`
- [ ] T006 [US1] Create `Exclusion` domain (immutable case class + smart ctor → `Either[AssetDomainError, Exclusion]`; required `entityId: Long`; `matchType: ExclusionMatchType` via `fromValue`; non-blank `value`/`reason`; `isActive=true`, `createdBy="system"` reflecting DB defaults; read-only timestamps) in `app/drp/asset/domain/Exclusion.scala` (depends on T002, T005)
- [ ] T007 [P] [US1] Define the `ExclusionRepository` port (`create`, `listByEntity`, `existsActiveDuplicate`) in `app/drp/asset/application/ports/ExclusionRepository.scala`
- [ ] T008 [US1] Create `ExclusionsTable` + `ExclusionRow` (Slick via `MonaPgProfile`, mapping the `exclusions` columns; `entity_id` as `Option[Long]`; insert omits `is_active`/`created_by`/`created_at`/`updated_at` so DB defaults + trigger apply) in `app/drp/asset/infrastructure/ExclusionsTable.scala`
- [ ] T009 [US1] Implement `SlickExclusionRepository` (`@NamedDatabase("drp")`; insert-returning-id with read-back; `listByEntity` bulk ordered by id; `existsActiveDuplicate` single query) in `app/drp/asset/infrastructure/SlickExclusionRepository.scala` (depends on T007, T008)
- [ ] T010 [P] [US1] Implement `InMemoryExclusionRepository` test adapter (assigns id/timestamps, `is_active=true`, `created_by="system"`; enforces the active-duplicate guard in memory) in `app/drp/asset/infrastructure/InMemoryExclusionRepository.scala` (depends on T007)
- [ ] T011 [US1] Implement `ExclusionService` + `ExclusionServiceImpl` (`register`: domain-validate → check owning entity via the existing `EntityRepository.existsById` (→ `UnknownEntity`) → `existsActiveDuplicate` pre-check (→ `DuplicateActiveExclusion`) → persist; `listByEntity`) in `app/drp/asset/application/ExclusionService.scala` and `ExclusionServiceImpl.scala` (depends on T006, T007; reuses US-001's `EntityRepository` port)
- [ ] T012 [US1] Extend Guice `AssetModule` with `bind(ExclusionService).to(ExclusionServiceImpl)` and `bind(ExclusionRepository).to(SlickExclusionRepository)` in `app/drp/asset/application/AssetModule.scala` (additive; leaves the entity bindings intact) (depends on T009, T011)
- [ ] T013 [P] [US1] Create `ExclusionFormData` form (`value` nonEmptyText, `matchType` nonEmptyText default "exact", `reason` nonEmptyText; `entityId` comes from the path, not the form) in `app/drp/asset/web/ExclusionFormData.scala`
- [ ] T014 [US1] Create `ExclusionController` — `list(entityId)` (GET; guard with `EntityRepository.existsById`/service, redirect to `/drp/assets` with a visible error if the entity is missing; else render the view with the entity's exclusions) and `create(entityId)` (POST → 303 back to the listing on success; blank value / blank reason / out-of-set match type / unknown entity / active duplicate each redirect back with a visible flash message, nothing persisted) in `app/drp/asset/web/ExclusionController.scala` (depends on T011, T013)
- [ ] T015 [US1] Create the dedicated per-entity view `exclusions.scala.html` (header "Exclusions for entity #<id>"; flash success/error; create form with a `matchType` select of the four values + `value` + `reason`; list each exclusion's value/match type/reason/active/timestamps; "No exclusions yet." when empty) in `app/drp/asset/web/views/exclusions.scala.html` (depends on T014)
- [ ] T016 [US1] Add routes `GET /drp/assets/entities/:entityId/exclusions` and `POST /drp/assets/entities/:entityId/exclusions` to `conf/routes` (additive; do not modify the existing `/drp/assets` routes) (depends on T014, T015)

**Checkpoint**: US1 fully functional and demoable — register and view per-entity exclusions. MVP complete.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [ ] T017 [P] Run `sbt test` and confirm green — new `drp.asset` exclusion specs AND all pre-existing suites (todo + US-001 `EntitySpec`/`EntityServiceSpec`) pass (Constitution Dev-Workflow)
- [ ] T018 Execute `quickstart.md` scenarios 1–10 against `sbt run` and confirm each expected outcome (incl. zero rows in `exclusions` on every rejection path: scenarios 2, 3, 4, 6, 8)
- [ ] T019 [P] Scope/architecture self-check: `app/drp/asset/web/views/list.scala.html` and all US-001 entity/asset files are unmodified; `slick.dbs.default` and `app/todo/**` untouched; `Exclusion` domain has no Play/Slick/HTTP/JSON imports; the listing/duplicate paths make no DB call inside a loop; `ExclusionServiceImpl` reuses the same-module `EntityRepository` (no cross-module DI); `created_by` is left to the DB `'system'` default (Constitution I, II, V)

---

## Dependencies & Execution Order

### Phase order

- **Setup (P1)** → **Foundational (P2)** → **US1 (P3)** → **Polish (P4)**.
- Single user story: once Foundational is done, all of US1 proceeds; there is no cross-story dependency.

### Key task dependencies

- T006 ← T002, T005 · T009 ← T007, T008 · T010 ← T007 · T011 ← T006, T007 (+ existing `EntityRepository`)
- T012 ← T009, T011 · T014 ← T011, T013 · T015 ← T014 · T016 ← T014, T015
- T008 uses the existing `MonaPgProfile` (confirmed in T001); no task dependency.

### Parallel opportunities

- Tests: T003, T004 [P] (different files).
- Domain/ports/form: T005, T007, T013 [P]; T010 [P] after T007 (different files from T009/T011).
- Polish: T017, T019 [P].

---

## Parallel Example: User Story 1

```text
# After Foundational (T002), launch in parallel (different files, no incomplete deps):
T003  Domain spec   → test/drp/asset/domain/ExclusionSpec.scala
T004  Service spec  → test/drp/asset/application/ExclusionServiceSpec.scala
T005  ExclusionMatchType ADT → app/drp/asset/domain/ExclusionMatchType.scala
T007  ExclusionRepository port → app/drp/asset/application/ports/ExclusionRepository.scala
T013  ExclusionFormData → app/drp/asset/web/ExclusionFormData.scala
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 Setup (verify) → 2. Phase 2 Foundational (T002) → 3. Phase 3 US1 → **STOP & VALIDATE** (register + view per-entity exclusions; quickstart 1–10). Demoable MVP — this single story IS the feature.

### Incremental Delivery

- Setup + Foundational → US1 → commit/checkpoint (Constitution: commit after each approved phase) → Polish.
- Each phase is an atomic, independently committable increment.

---

## Notes

- `[P]` = different files, no incomplete dependency.
- `Exclusion` domain stays pure (no Play/Slick/HTTP/JSON/DB imports) — Slick lives only in `infrastructure`, forms/controllers only in `web`.
- No migration is authored or run — the `exclusions` table comes from the already-applied `V001`.
- US-001 is reused, not modified: the `/drp/assets` view, entity/asset code, and `slick.dbs.default` stay untouched (a nav link to the exclusions screen is deferred — plan D5).
- Commit after each task or logical group; stop at the checkpoint to validate independently.
- `/speckit-analyze` MUST run before `/speckit-implement` (constitution gate).
