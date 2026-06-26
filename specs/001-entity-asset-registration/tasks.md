---
description: "Task list for Protected Entity & Asset Registration"
---

# Tasks: Protected Entity & Asset Registration

**Input**: Design documents from `specs/001-entity-asset-registration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/http-routes.md, quickstart.md

**Tests**: ScalaTest specs ARE included — required by research.md (D8) and the constitution's Dev-Workflow rule (suites must stay green; in-memory repos enable DB-less service tests).

**Organization**: Grouped by user story (US1 = entity registration P1; US2 = asset registration P2).

## Format: `[ID] [P?] [Story] Description with file path`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 (setup, foundational, polish carry no story label)

## Path Conventions

Modular monolith: feature code under `app/drp/asset/`, shared Slick profile under `app/drp/shared/infrastructure/`, tests under `test/drp/asset/`, root wiring in `build.sbt` / `conf/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: PostgreSQL/Slick wiring both stories depend on. Does NOT touch `slick.dbs.default` (todo / SQL Server).

- [X] T001 Add the PostgreSQL JDBC driver (`org.postgresql % postgresql`) and slick-pg (`com.github.tminglei %% slick-pg` + its play-json artifact) to `build.sbt` libraryDependencies
- [X] T002 Add a dedicated `slick.dbs.drp` Slick database (PostgreSQL profile, JDBC URL/user/password sourced from `.env`, port 55432) to `conf/application.conf`, leaving the existing `slick.dbs.default` (SQL Server) block untouched
- [X] T003 [P] Create `MonaPgProfile` (extends slick-pg `ExPostgresProfile` with Play-JSON JSONB support) in `app/drp/shared/infrastructure/MonaPgProfile.scala`

**Checkpoint**: `sbt compile` succeeds with the new deps; `slick.dbs.drp` resolves.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared domain pieces both stories use. ⚠️ Complete before US1/US2.

- [X] T004 Create the shared domain error ADT in `app/drp/asset/domain/AssetDomainError.scala` (cases: `BlankEntityName`, `BlankAssetValue`, `UnknownEntity`, `DuplicateActiveAsset`)

**Checkpoint**: Foundation ready — user stories can begin.

---

## Phase 3: User Story 1 - Register a protected entity (Priority: P1) 🎯 MVP

**Goal**: An analyst registers an entity (name + free-text type) and sees it on the listing screen.

**Independent Test**: Register "Akbank" (type "brand"); it persists and appears on `GET /drp/assets`. Blank name is rejected with a visible message and persists nothing. (quickstart scenarios 1, 4, 6, 8, 9)

### Tests for User Story 1

- [X] T005 [P] [US1] Domain spec for `Entity` smart constructor (blank name → `BlankEntityName`; any non-blank type accepted) in `test/drp/asset/domain/EntitySpec.scala`
- [X] T006 [P] [US1] Service spec for `EntityService` via `InMemoryEntityRepository` (register persists + appears in list; blank name → error, nothing stored) in `test/drp/asset/application/EntityServiceSpec.scala`

### Implementation for User Story 1

- [X] T007 [P] [US1] Create `Entity` domain (immutable case class + smart ctor returning `Either[AssetDomainError, Entity]`; free-text type, read-only timestamps) in `app/drp/asset/domain/Entity.scala`
- [X] T008 [P] [US1] Define `EntityRepository` port (`create`, `listAll`) in `app/drp/asset/application/ports/EntityRepository.scala`
- [X] T009 [US1] Create `EntitiesTable` + `EntityRow` (Slick via `MonaPgProfile`, mapping the `entities` columns; insert omits `created_at`/`updated_at`) in `app/drp/asset/infrastructure/EntitiesTable.scala`
- [X] T010 [US1] Implement `SlickEntityRepository` (`@NamedDatabase("drp")`; insert-returning-id, `listAll` bulk) in `app/drp/asset/infrastructure/SlickEntityRepository.scala` (depends on T008, T009)
- [X] T011 [P] [US1] Implement `InMemoryEntityRepository` test adapter in `app/drp/asset/infrastructure/InMemoryEntityRepository.scala` (depends on T008)
- [X] T012 [US1] Implement `EntityService` + `EntityServiceImpl` (`register`, `list` → `Future[Either[AssetDomainError, _]]`) in `app/drp/asset/application/EntityService.scala` and `EntityServiceImpl.scala` (depends on T007, T008)
- [X] T013 [US1] Create Guice `AssetModule` binding `EntityRepository → SlickEntityRepository`, and register it via `play.modules.enabled += "drp.asset.application.AssetModule"` in `conf/application.conf`, file `app/drp/asset/application/AssetModule.scala` (depends on T010)
- [X] T014 [US1] Create `EntityController.create` (POST → 303 redirect to listing; blank name re-renders with a visible message, nothing persisted) + `EntityFormData` in `app/drp/asset/web/EntityController.scala` and `app/drp/asset/web/EntityFormData.scala` (depends on T012)
- [X] T015 [US1] Create `AssetController.list` (GET `/drp/assets`) rendering `list.scala.html` — lists all entities, each with an (empty for now) asset list, never erroring on zero assets — in `app/drp/asset/web/AssetController.scala` and `app/drp/asset/web/views/list.scala.html` (depends on T012)
- [X] T016 [US1] Add routes `GET /drp/assets` and `POST /drp/assets/entities` to `conf/routes` (depends on T014, T015)

**Checkpoint**: US1 fully functional and demoable — register entities and view them. MVP complete.

---

## Phase 4: User Story 2 - Register domain assets under an entity (Priority: P2)

**Goal**: Under an existing entity, the analyst registers domain assets (value + optional reference metadata) and sees them under their entity.

**Independent Test**: With "Akbank" present, register "akbank.com" → shown under Akbank as `domain`, active, value as-entered. Blank value, unknown entity, and a second active duplicate are each rejected with a visible message and persist nothing. (quickstart scenarios 2, 3, 5, 7, 10)

**Depends on US1**: reuses `EntityRepository` (entity-existence check) and extends the listing view/`AssetController`.

### Tests for User Story 2

- [ ] T017 [P] [US2] Domain spec for `Asset` / `AssetType` codec / `AssetMetadata` (blank value → `BlankAssetValue`; metadata accepts only reference fields; `domain`/`subdomain` codec round-trips) in `test/drp/asset/domain/AssetSpec.scala`
- [ ] T018 [P] [US2] Service spec for `AssetService` via in-memory repos (register under existing entity; blank value, unknown entity, and active duplicate each → error with nothing stored) in `test/drp/asset/application/AssetServiceSpec.scala`

### Implementation for User Story 2

- [ ] T019 [P] [US2] Create `AssetType` sealed ADT + string codec (`Domain`↔"domain", `Subdomain`↔"subdomain") in `app/drp/asset/domain/AssetType.scala`
- [ ] T020 [P] [US2] Create `AssetMetadata` references-only value object (optional `homepageUrl`/`loginPageUrl`/`logoRef`/`faviconRef`/`referenceDomSummary`) in `app/drp/asset/domain/AssetMetadata.scala`
- [ ] T021 [US2] Create `Asset` domain (immutable case class + smart ctor → `Either[AssetDomainError, Asset]`; `is_active` defaults true) in `app/drp/asset/domain/Asset.scala` (depends on T019, T020)
- [ ] T022 [P] [US2] Define `AssetRepository` port (`create`, `listAll`/`listByEntityIds`, `existsActiveDuplicate`) in `app/drp/asset/application/ports/AssetRepository.scala`
- [ ] T023 [US2] Create `AssetsTable` + `AssetRow` (Slick via `MonaPgProfile`; `metadata` as JSONB) mapping the `assets` columns in `app/drp/asset/infrastructure/AssetsTable.scala` (depends on T003)
- [ ] T024 [US2] Implement `AssetMappers` — Row↔domain incl. JSONB `metadata` codec that encodes only reference fields and rejects binary/base64 content — in `app/drp/asset/infrastructure/AssetMappers.scala` (depends on T020, T023)
- [ ] T025 [US2] Implement `SlickAssetRepository` (`@NamedDatabase("drp")`; insert; bulk `listAll`; surface the active-duplicate unique-index violation as `DuplicateActiveAsset`) in `app/drp/asset/infrastructure/SlickAssetRepository.scala` (depends on T022, T023, T024)
- [ ] T026 [P] [US2] Implement `InMemoryAssetRepository` test adapter (in-memory active-duplicate check) in `app/drp/asset/infrastructure/InMemoryAssetRepository.scala` (depends on T022)
- [ ] T027 [US2] Implement `AssetService` + `AssetServiceImpl` (validate entity exists via `EntityRepository`; blank value; active-duplicate guard) in `app/drp/asset/application/AssetService.scala` and `AssetServiceImpl.scala` (depends on T021, T022, and US1's `EntityRepository` T008)
- [ ] T028 [US2] Extend `AssetModule` to also bind `AssetRepository → SlickAssetRepository` in `app/drp/asset/application/AssetModule.scala` (depends on T025)
- [ ] T029 [US2] Create `AssetController.create` (POST `/drp/assets/entities/:entityId/assets` → 303; blank value / unknown entity / duplicate re-render with a visible message, nothing persisted) + `AssetFormData` in `app/drp/asset/web/AssetController.scala` and `app/drp/asset/web/AssetFormData.scala` (depends on T027)
- [ ] T030 [US2] Extend `list.scala.html` to render each entity's assets and host the asset form, and add route `POST /drp/assets/entities/:entityId/assets` to `conf/routes` (depends on T029)

**Checkpoint**: US1 + US2 both work — register entities and domain assets, view both.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T031 [P] Run `sbt test` and confirm green — new `drp.asset` specs AND all pre-existing todo suites pass (Constitution Dev-Workflow)
- [ ] T032 Execute `quickstart.md` scenarios 1–10 against `sbt run` and confirm each expected outcome (incl. zero rows on every rejection path)
- [ ] T033 [P] Scope/architecture self-check: `slick.dbs.default` and `app/todo/**` are unmodified; no DB call inside a loop in the listing; domain has no Play/Slick/HTTP imports (Constitution I, V)

---

## Dependencies & Execution Order

### Phase order

- **Setup (P1)** → **Foundational (P2)** → **US1 (P3)** → **US2 (P4)** → **Polish (P5)**.
- US2 depends on US1 (reuses `EntityRepository`; extends the listing view). It is independently *testable* (its own specs use in-memory repos) but is sequenced after US1 for the shared screen.

### Key task dependencies

- T010 ← T008, T009 · T012 ← T007, T008 · T013 ← T010 · T014/T015 ← T012 · T016 ← T014, T015
- T021 ← T019, T020 · T024 ← T020, T023 · T025 ← T022, T023, T024 · T027 ← T021, T022, T008(US1) · T028 ← T025 · T029 ← T027 · T030 ← T029
- All of US2's infra/Slick tasks depend on T003 (`MonaPgProfile`).

### Parallel opportunities

- Setup: T003 [P] alongside T001/T002 review.
- US1: T005, T006 (tests) [P]; then T007, T008, T011 [P] (different files).
- US2: T017, T018 (tests) [P]; T019, T020, T022, T026 [P] (different files).
- Polish: T031, T033 [P].

---

## Parallel Example: User Story 1

```text
# After Foundational, launch in parallel (different files, no incomplete deps):
T005  Domain spec  → test/drp/asset/domain/EntitySpec.scala
T006  Service spec → test/drp/asset/application/EntityServiceSpec.scala
T007  Entity domain → app/drp/asset/domain/Entity.scala
T008  EntityRepository port → app/drp/asset/application/ports/EntityRepository.scala
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → **STOP & VALIDATE** (register + view entities; quickstart 1,4,6,8,9). Demoable MVP.

### Incremental Delivery

- US1 → commit/checkpoint (Constitution: commit after each approved phase) → US2 → commit → Polish.
- Each phase is an atomic, independently committable increment.

---

## Notes

- `[P]` = different files, no incomplete dependency.
- Domain stays pure (no Play/Slick/HTTP/JSON/DB imports) — Slick/JSON live only in `infrastructure`.
- No migration is authored or run — the `entities`/`assets` tables come from the already-applied `V001`.
- Commit after each task or logical group; stop at any checkpoint to validate independently.
- `/speckit-analyze` MUST run before `/speckit-implement` (constitution gate).
