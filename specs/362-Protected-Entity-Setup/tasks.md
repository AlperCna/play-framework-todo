# Tasks: Protected Entity Setup — Entity, Asset, Asset Group & Exclusion Management

**Feature**: `specs/362-Protected-Entity-Setup/` | **Branch**: `feature/362-Protected-Entity-Setup`
**Plan**: [plan.md](./plan.md) · **Spec**: [spec.md](./spec.md) · **Data model**: [data-model.md](./data-model.md) · **Contracts**: [ports.md](./contracts/ports.md), [web-routes.md](./contracts/web-routes.md)

**Tests**: REQUIRED for this code-producing feature (constitution Dev-Workflow + tasks-template policy): each new repository **port** ships an in-memory test adapter, and each user story gets a service-level ScalaTest spec. Domain smart-constructors also get specs.

**Conventions**: `app/drp/asset/` (module) + `app/drp/shared/` (DRP primitives — no `app/todo` import). `domain` is pure. Migrations are written, NEVER executed by the agent.

---

## Phase 1: Setup (build + datasource)

- [X] T001 Add PostgreSQL deps to `build.sbt`: `"com.github.tminglei" %% "slick-pg" % "0.21.x"` and `"org.postgresql" % "postgresql" % "42.7.x"` (verify Slick-3.4 compatibility on first resolve).
- [X] T002 Add the `slick.dbs.drp` datasource to `conf/application.conf` (profile `drp.shared.infrastructure.MonaPgProfile$`, PostgreSQL driver, URL/user/password from `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` env). Leave `slick.dbs.default` (SQL Server / todo) untouched.

---

## Phase 2: Foundational (blocks ALL user stories)

**⚠️ No user story can start until this phase is complete.**

- [X] T003 [P] Create `app/drp/shared/infrastructure/MonaPgProfile.scala` — slick-pg `ExPostgresProfile` + JSON(B) support.
- [X] T004 [P] Create `app/drp/shared/domain/DomainError.scala` — sealed base (`code` + `message`) with values from data-model (`EmptyEntityName`, `EmptyAssetValue`, `EmptyAssetGroupName`, `EmptyExclusionValue`, `EntityNotFound`, `AssetGroupNotFound`, `DuplicateEntityName`, `DuplicateAsset`, `DuplicateExclusion`, `AssetGroupEntityMismatch`).
- [X] T005 [P] Create `app/drp/shared/application/ServiceResult.scala` — `Future[Either[DomainError, A]]` wrapper (`map`/`flatMap`/`fromEither`/`fromFuture`/`fromOptionF`), mirroring the todo pattern (no todo import).
- [X] T006 [P] Create `app/drp/shared/application/Page.scala` and `PageRequest.scala` — pagination value types.
- [X] T007 [P] Create `app/drp/shared/application/Clock.scala` (+ `SystemClock`) — `now(): Instant` seam for testable timestamps.
- [X] T008 [P] Create `app/drp/shared/web/views/main.scala.html` — base DRP Twirl layout (`@main(title){ … }`).
- [X] T009 [P] Write migration `app/migrations/drp-postgres/V007__entity_name_unique_up.sql` (`CREATE UNIQUE INDEX uq_entities_name ON entities(name);`) and `V007__entity_name_unique_down.sql` (`DROP INDEX uq_entities_name;`). **Do NOT execute** — a human applies it.
- [X] T010 Create `app/drp/asset/infrastructure/AssetModule.scala` — Guice module skeleton with a mode-aware persistence switch (Test → in-memory repo bindings, else → Slick repo bindings); service bindings added per story.
- [X] T011 Create `app/drp/boot/DrpModule.scala` (bind `Clock`→`SystemClock`; `install(new AssetModule)`) and register it in `conf/application.conf` (`play.modules.enabled += "drp.boot.DrpModule"`).

**Checkpoint**: project compiles, app boots, `drp` datasource resolves lazily; todo path unaffected.

---

## Phase 3: User Story 1 — Register & view a protected entity (Priority: P1) 🎯 MVP

**Goal**: Create/edit/view entities; paginated entity list; no delete.
**Independent test**: Create "Akbank"/"brand" → appears in paginated list; duplicate name rejected; blank name rejected.

- [X] T012 [P] [US1] `app/drp/asset/domain/EntityType.scala` — open enum (`Brand`/`Person`/`Institution`/`Other(raw)`) + String codec.
- [X] T013 [P] [US1] `app/drp/asset/domain/Entity.scala` — `EntityId` + `final case class Entity` + `Entity.create(name, type)` smart ctor (`Either[DomainError, Entity]`, blank name → `EmptyEntityName`).
- [X] T014 [P] [US1] `test/drp/asset/domain/EntitySpec.scala` — smart-ctor validation (valid create; blank name → `EmptyEntityName`; type codec round-trip incl. unknown→`Other`).
- [X] T015 [US1] `app/drp/asset/application/ports/EntityRepository.scala` — port (`add`/`get`/`existsById`/`existsByName`/`update`/`list(PageRequest)`).
- [X] T016 [US1] `app/drp/asset/infrastructure/inmemory/InMemoryEntityRepository.scala` — test adapter (assigns id/timestamps; `existsByName`).
- [X] T017 [US1] `app/drp/asset/application/EntityService.scala` + `EntityServiceImpl` — create/update/get/`list(page)`; duplicate-name pre-check → `DuplicateEntityName`.
- [X] T018 [P] [US1] `test/drp/asset/application/EntityServiceSpec.scala` via `InMemoryEntityRepository` — register persists + appears in list; duplicate name → error, nothing written; blank → error; edit changes fields (created stable).
- [X] T019 [US1] `app/drp/asset/infrastructure/tables/EntitiesTable.scala` (Slick/MonaPgProfile) + `app/drp/asset/infrastructure/slick/SlickEntityRepository.scala` on the `drp` datasource.
- [X] T020 [US1] Wire `EntityService`→`EntityServiceImpl` and `EntityRepository`→(Slick/InMemory by mode) in `AssetModule`.
- [X] T021 [P] [US1] `app/drp/asset/web/EntityFormData.scala` (+ Play `Form`) and `EntityViewModel.scala`.
- [X] T022 [US1] `app/drp/asset/web/EntityController.scala` — `list(page)` / `newForm` / `create` / `view` / `editForm` / `update` (no delete); domain-error → global form error.
- [X] T023 [P] [US1] Twirl views `app/drp/asset/web/views/{entitiesList,entityForm,entityView}.scala.html` using the shared `main` layout + `@helper.CSRF.formField`.
- [X] T024 [US1] Add `/drp/entities` routes (list/new/create/view/edit/update) to `conf/routes` under a `# DRP 362` comment group.

**Checkpoint**: US1 independently usable — paginated entity CRUD (no delete).

---

## Phase 4: User Story 2 — Register & view assets under an entity (Priority: P2)

**Goal**: Assets under an entity with discrete reference metadata; per-entity duplicate + parent + same-entity-group rules.
**Independent test**: Under "Akbank" add asset `domain`/`akbank.com` (refs) → active, listed; duplicate tuple rejected; invalid parent rejected.

- [X] T025 [P] [US2] `app/drp/asset/domain/AssetType.scala` (closed enum `domain`/`subdomain` + codec) and `AssetMetadata.scala` (homepage/login/logo/favicon optional refs).
- [X] T026 [P] [US2] `app/drp/asset/domain/Asset.scala` — `AssetId` + `final case class Asset` + `Asset.create(...)` (blank value → `EmptyAssetValue`; `is_active=true`).
- [X] T027 [P] [US2] `app/drp/asset/infrastructure/AssetMetadataCodec.scala` — `AssetMetadata` ⇄ JSONB (references only).
- [X] T028 [P] [US2] `test/drp/asset/domain/AssetSpec.scala` — smart-ctor (blank value → error; metadata assembled from discrete refs).
- [X] T029 [US2] `app/drp/asset/application/ports/AssetRepository.scala` — port (`add`/`get`/`existsActive(entity,assetType,value)`/`update`/`listByEntity`).
- [X] T030 [US2] `app/drp/asset/infrastructure/inmemory/InMemoryAssetRepository.scala` — active `(entity,asset_type,value)` dup check.
- [X] T031 [US2] `app/drp/asset/application/AssetService.scala` + impl — parent entity exists (`EntityNotFound`); group same-entity (`AssetGroupEntityMismatch`); duplicate (`DuplicateAsset`).
- [X] T032 [P] [US2] `test/drp/asset/application/AssetServiceSpec.scala` via in-memory repos — register; duplicate tuple rejected; invalid parent rejected; cross-entity group prevented; blank value rejected.
- [X] T033 [US2] `app/drp/asset/infrastructure/tables/AssetsTable.scala` (Slick, JSONB metadata via MonaPgProfile) + `slick/SlickAssetRepository.scala`.
- [X] T034 [US2] Wire `AssetService` + `AssetRepository` in `AssetModule`.
- [X] T035 [P] [US2] `app/drp/asset/web/AssetFormData.scala` (discrete metadata fields) + `AssetViewModel.scala`.
- [X] T036 [US2] `app/drp/asset/web/AssetController.scala` — newForm/create/editForm/update under an entity.
- [X] T037 [P] [US2] Twirl `app/drp/asset/web/views/assetForm.scala.html` + render assets (full) inside `entityView`.
- [X] T038 [US2] Add `/drp/entities/:entityId/assets` + `/drp/assets/:id/edit|update` routes to `conf/routes`.

**Checkpoint**: US1 + US2 work independently.

---

## Phase 5: User Story 3 — Declare exclusions + read seam (Priority: P2)

**Goal**: Entity-scoped exclusions stored verbatim (`created_by="system"`), never evaluated; define the read seam.
**Independent test**: Under "Akbank" add exclusion `akbankdirekt.com`/`exact`/`owned_unmonitored` → stored verbatim, `created_by="system"`; `activeExclusions(entity)` returns it; no matching.

- [X] T039 [P] [US3] `app/drp/asset/domain/MatchType.scala` (closed enum + codec) and `ExclusionReason.scala` (open enum + codec).
- [X] T040 [P] [US3] `app/drp/asset/domain/Exclusion.scala` — `ExclusionId` + `Exclusion.create(entityId, value, matchType, reason)` (entityId required; blank value → error; `createdBy="system"`).
- [X] T041 [P] [US3] `test/drp/asset/domain/ExclusionSpec.scala` — smart-ctor (blank value → error; entity required; verbatim value retained).
- [X] T042 [US3] `app/drp/asset/application/ports/ExclusionRepository.scala` — port (`add`/`get`/`existsActive`/`update`/`listActiveByEntity`).
- [X] T043 [US3] `app/drp/asset/infrastructure/inmemory/InMemoryExclusionRepository.scala` — active `(entity,value,match_type)` dup check.
- [X] T044 [US3] `app/drp/asset/application/ExclusionService.scala` + impl — entity-scoped (parent exists); verbatim; never evaluated.
- [X] T045 [US3] `app/drp/asset/application/ports/AssetReadPort.scala` (+ read-models `ExclusionView`, `EntityWithAssets`) + impl — `activeExclusions(entityId)`, `resolveEntityWithAssets(entityId)` (typed read-models; no domain/Slick leak).
- [X] T046 [P] [US3] `test/drp/asset/application/ExclusionServiceSpec.scala` + `AssetReadPortSpec` via in-memory repos — register verbatim + `system`; invalid parent rejected; `activeExclusions` returns exactly the entity's active set; no matching applied.
- [X] T047 [US3] `app/drp/asset/infrastructure/tables/ExclusionsTable.scala` (Slick) + `slick/SlickExclusionRepository.scala` + Slick `AssetReadPort` impl.
- [X] T048 [US3] Wire `ExclusionService` + `ExclusionRepository` + `AssetReadPort` in `AssetModule`.
- [X] T049 [P] [US3] `app/drp/asset/web/ExclusionFormData.scala` + `ExclusionViewModel.scala`.
- [X] T050 [US3] `app/drp/asset/web/ExclusionController.scala` — newForm/create/editForm/update.
- [X] T051 [P] [US3] Twirl `app/drp/asset/web/views/exclusionForm.scala.html` + render active exclusions inside `entityView`.
- [X] T052 [US3] Add `/drp/entities/:entityId/exclusions` + `/drp/exclusions/:id/edit|update` routes to `conf/routes`.

**Checkpoint**: US1–US3 work; the discovery read seam is defined.

---

## Phase 6: User Story 4 — Organize assets into asset groups (Priority: P3)

**Goal**: Optional groups under an entity; assets reference a same-entity group or none.
**Independent test**: Under "Akbank" create group "Akbank Direkt"; assign an asset; ungrouped asset also valid; cross-entity group prevented.

- [X] T053 [P] [US4] `app/drp/asset/domain/AssetGroup.scala` — `AssetGroupId` + `AssetGroup.create(entityId, name)` (blank name → error).
- [X] T054 [P] [US4] `test/drp/asset/domain/AssetGroupSpec.scala` — smart-ctor validation.
- [X] T055 [US4] `app/drp/asset/application/ports/AssetGroupRepository.scala` — port (`add`/`get`/`update`/`listByEntity`).
- [X] T056 [US4] `app/drp/asset/infrastructure/inmemory/InMemoryAssetGroupRepository.scala`.
- [X] T057 [US4] `app/drp/asset/application/AssetGroupService.scala` + impl — parent entity exists; unique `(entity,name)` pre-check.
- [X] T058 [P] [US4] `test/drp/asset/application/AssetGroupServiceSpec.scala` via in-memory repos — create; same-entity assignment OK; cross-entity assignment prevented (with `AssetService`).
- [X] T059 [US4] `app/drp/asset/infrastructure/tables/AssetGroupsTable.scala` (Slick) + `slick/SlickAssetGroupRepository.scala`.
- [X] T060 [US4] Wire `AssetGroupService` + `AssetGroupRepository` in `AssetModule`; expose group selection in `AssetController`/`AssetFormData`.
- [X] T061 [P] [US4] `app/drp/asset/web/AssetGroupFormData.scala` + `AssetGroupViewModel.scala`.
- [X] T062 [US4] `app/drp/asset/web/AssetGroupController.scala` — newForm/create/editForm/update.
- [X] T063 [P] [US4] Twirl `app/drp/asset/web/views/assetGroupForm.scala.html` + group select in `assetForm` + render groups in `entityView`.
- [X] T064 [US4] Add `/drp/entities/:entityId/asset-groups` + `/drp/asset-groups/:id/edit|update` routes to `conf/routes`.

**Checkpoint**: all four record types work end-to-end.

---

## Phase 7: Polish & Cross-Cutting

- [X] T065 [P] Add short WHAT comments to public ports/traits/services where the signature isn't self-evident (Constitution V).
- [X] T066 [P] Add i18n keys for every `DomainError.code` + form labels to `conf/messages` / `conf/messages.tr` / `conf/messages.en`.
- [X] T067 Scope/architecture self-check: `app/drp/**/domain` has no Play/Slick/JSON imports; **no `todo.*` import anywhere in `app/drp`**; `slick.dbs.default` + `app/todo/**` unmodified; no delete route/action anywhere; entity list paginated, per-entity asset/exclusion lists bulk (no DB-call-in-loop); `metadata` references-only; `V007` written but NOT executed (Constitution I, II, IV, Migration discipline).
- [ ] T068 Run `sbt test` (all suites green) and execute the `quickstart.md` manual walk-through (after a human applies `V001..V007`).
- [X] T069 [P] Note in `quickstart.md`/PR description that `V007` must be applied by a human before running.

---

## Dependencies & Execution Order

- **Phase 1 (Setup)** → **Phase 2 (Foundational)** block everything.
- **US1 (P1)** depends only on Foundational. **US2/US3/US4** depend on Foundational; they also reuse `EntityRepository.existsById` from US1 (so US1 ships first), but each is independently testable via in-memory repos.
- **US4** interacts with US2 (asset↔group assignment) — sequence US4 after US2 for the shared asset form, though its own specs stand alone.
- Within a story: domain → port → in-memory adapter → service spec (fails) → service impl → Slick adapter → web → routes → wiring.

## Parallel Opportunities

- All of Phase 2 `[P]` (T003–T009) can run together (distinct files).
- Within each story, `[P]` domain/codec/test/view-model/Twirl tasks run together; the port→service→Slick→controller chain is sequential (shared/ dependent files).
- `AssetModule` (T020/T034/T048/T060), `conf/routes` (T024/T038/T052/T064), and `entityView` Twirl are shared files → those tasks are sequential, not `[P]`.

## Implementation Strategy

- **MVP** = Phase 1 + Phase 2 + **US1** (paginated entity CRUD). Demoable on its own.
- **Incremental**: add US2 (assets) → US3 (exclusions + read seam) → US4 (groups), committing per phase (Constitution: git checkpoint per approved phase).
- Run `/speckit-analyze` before `/speckit-implement` (constitution gate). `V007` is written here but applied by a human.

**Total: 69 tasks** — Setup 2, Foundational 9, US1 13, US2 14, US3 14, US4 12, Polish 5.
