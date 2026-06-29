# Implementation Plan: Protected Entity Setup — Entity, Asset, Asset Group & Exclusion Management

**Branch**: `feature/362-Protected-Entity-Setup` | **Date**: 2026-06-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/362-Protected-Entity-Setup/spec.md`

## Summary

Build the first real DRP module — `app/drp/asset/` — owning four tables (`entities`, `asset_groups`, `assets`, `exclusions`) with synchronous Twirl CRUD (create / edit / list / view, **no delete**) and a defined read seam for the future discovery module. The tables already exist in migration `V001__asset_layer`; this feature stands up the **Scala/Slick foundation on PostgreSQL** (slick-pg + a `drp` datasource + `MonaPgProfile`), mirrors the established todo patterns inside a self-contained `app/drp/shared` (no dependency on `app/todo`), and adds one small migration (`V007`) to enforce the `entities.name` uniqueness the US requires but the v5 baseline omitted.

## Technical Context

**Language/Version**: Scala 2.13.18 / Play Framework 2.9
**Primary Dependencies**: play-slick 5.2.0 (Slick 3.4.x); **add** `slick-pg` 0.21.x (Slick-3.4 line) + `org.postgresql:postgresql` 42.7.x; Guice; Twirl. pac4j present but NOT used by this feature (no auth gate — see spec Clarifications).
**Storage**: PostgreSQL (Docker `ghcr.io/pgmq/pg18-pgmq`, port 55432; connection from `.env` — `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`). New `slick.dbs.drp` datasource on `MonaPgProfile` (slick-pg, JSONB). The existing `slick.dbs.default` (SQL Server / todo) is left untouched.
**Testing**: ScalaTest + scalatestplus-play. Service + domain specs run against **in-memory repository adapters** (one per port) — no live DB. PostgreSQL/Slick path validated manually via `quickstart.md`.
**Target Platform**: Server-rendered web (Twirl SSR), single-JVM modular monolith.
**Project Type**: Web application (server-rendered) within a modular monolith.
**Performance Goals**: None numeric (synchronous CRUD, demo scale). Listing avoids N+1 (bulk fetch + in-memory grouping); entity list paginated.
**Constraints**: Fully synchronous (no PGMQ/JobQueue/worker); no auth gate this feature; `metadata` references-only (no binary); no delete anywhere; conform to the v5 schema as realized by `V001`.
**Scale/Scope**: Single-analyst, single-tenant, demo scale. 4 tables, 4 CRUD surfaces, 1 read seam.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.* (Constitution v4.0.2)

| Principle | Status | Notes |
|---|---|---|
| I. Architecture Boundary | PASS | Code in `app/drp/asset/` + `app/drp/shared/`; pure `domain` (no Play/Slick/JSON); `web`→`application`→`domain`, `infrastructure`→`application/ports`. `asset` is the pipeline-root module (no upstream module deps). Single-Writer: `asset` owns all four tables. Does NOT import/build on `app/todo` — mirrors the patterns in a self-contained `drp.shared`. |
| II. Scope Boundary | PASS (with foundation) | Touches `app/drp/asset/**`, `app/drp/shared/**`, `conf/application.conf` (+`slick.dbs.drp`, +`play.modules.enabled` for `DrpModule`), `conf/routes` (+`/drp/...`), `build.sbt` (+slick-pg/postgres), and a new `V007` migration. Build/conf/foundation edits are required by the US "prove Play+Twirl+PostgreSQL+migration end-to-end" goal — declared in **Project Structure**. The existing `slick.dbs.default` and `app/todo/**` are NOT modified. |
| III. Single Responsibility | PASS | One service per record type; one responsibility per method. |
| IV. Data Access Discipline | PASS | Entity list **paginated** (`Page`/`PageRequest`); per-entity asset/exclusion lists loaded in full (one-parent bounded — IV exemption). Listing groups in memory (no N+1). DB unique indexes are the **race-safe authoritative guards** (V); service pre-checks are UX only. `metadata` JSONB holds references only — no large/binary content. No loop-with-DB-call. |
| V. Abstraction Reflected in Comments | PASS | Ports/traits/public services carry short WHAT comments; WHY only when non-obvious. |
| VI. Type-Conditional Behavior Preservation | PASS | `Type = Feature Implementation` (greenfield) → no behavior-preservation constraint beyond I & II; reuse established mechanisms (smart-constructor → `Either[DomainError,_]`, `ServiceResult`, repository port + Slick adapter, per-module Guice module). |
| Additional Constraints — Platform & Stack | PASS | Per-module Guice `Module` composed at root `DrpModule`; Twirl SSR (no React); immutable `final case class` + smart constructors; closed sets = `sealed`/open enum + codec, illegal-states-unrepresentable; no IO in `domain`; repository **port** in `application`, Slick adapter in `infrastructure`; module owns its own Slick tables (no growing `Tables` facade); no PostgreSQL ENUM (TEXT + CHECK as in `V001`); FK `ON DELETE RESTRICT` (V001); migrations hand-written (`V007` up/down) and **NOT executed by the agent**. Staging / queue / storage / scoring invariants: N/A here. |
| Dev-Workflow & Quality Gates | PASS | `/speckit-analyze` before implement; **each new repository port ships an in-memory test adapter**; ScalaTest stays green; commit per approved phase. |

**Result: PASS.** One justified deviation tracked below: adding `entities.name` UNIQUE via `V007`.

## Project Structure

### Documentation (this feature)

```text
specs/362-Protected-Entity-Setup/
├── plan.md              # This file
├── research.md          # Phase 0 — decisions (slick-pg, schema reconciliation, no-todo-reuse, testing)
├── data-model.md        # Phase 1 — 4 entities, value types, enums, validation rules
├── quickstart.md        # Phase 1 — migrate + run + click-through + ScalaTest validation
├── contracts/           # Phase 1 — repository ports, read seam, web routes
│   ├── ports.md
│   └── web-routes.md
└── tasks.md             # Phase 2 — /speckit-tasks (NOT created here)
```

### Source Code (repository root)

```text
app/drp/
├── shared/                                  # DRP common primitives (mirrors todo patterns; NOT imported from todo)
│   ├── domain/DomainError.scala             # sealed DRP domain-error base (code + message)
│   ├── application/ServiceResult.scala      # Future[Either[DomainError, A]] wrapper (map/flatMap)
│   ├── application/{Page.scala, PageRequest.scala}  # pagination value types
│   ├── application/Clock.scala              # now() seam (testable timestamps)
│   ├── infrastructure/MonaPgProfile.scala   # slick-pg ExPostgresProfile + JSONB support
│   ├── infrastructure/DrpSlickSupport.scala # shared Slick helpers (drp db handle, profile import)
│   ├── web/views/main.scala.html            # shared DRP Twirl layout
│   └── boot/DrpModule.scala                 # root DRP Guice module (composes asset + persistence)
└── asset/
    ├── domain/                              # PURE — no Play/Slick/JSON
    │   ├── Entity.scala / Asset.scala / AssetGroup.scala / Exclusion.scala  (final case class + smart ctor)
    │   ├── EntityType / AssetType / MatchType / ExclusionReason  (open enum + codec)
    │   └── AssetMetadata.scala              # typed references (homepage/login/logo/favicon)
    ├── application/
    │   ├── ports/{EntityRepository, AssetRepository, AssetGroupRepository, ExclusionRepository}.scala
    │   ├── ports/AssetReadPort.scala        # READ SEAM: activeExclusions(entityId), resolveEntityWithAssets(entityId) → read-models
    │   └── {Entity, Asset, AssetGroup, Exclusion}Service.scala (+ *Impl)
    ├── infrastructure/
    │   ├── tables/{Entities, AssetGroups, Assets, Exclusions}Table.scala  (Slick, MonaPgProfile)
    │   ├── slick/{SlickEntityRepository, ...}.scala        # real adapters (drp datasource)
    │   ├── inmemory/{InMemoryEntityRepository, ...}.scala  # test adapters (one per port)
    │   ├── AssetMetadataCodec.scala         # JSONB <-> AssetMetadata (references-only)
    │   └── AssetModule.scala                # asset Guice bindings; persistence switch (Slick vs InMemory)
    └── web/
        ├── {Entity, Asset, AssetGroup, Exclusion}Controller.scala
        ├── *FormData.scala                  # write models + Play Form
        ├── *ViewModel.scala                 # read models (no domain/persistence leak into views)
        └── views/*.scala.html

conf/
├── application.conf     # + slick.dbs.drp (PostgreSQL/MonaPgProfile, from .env); + play.modules.enabled += "drp.boot.DrpModule"
└── routes               # + /drp/entities, /drp/assets, /drp/asset-groups, /drp/exclusions (commented group)

build.sbt                # + "com.github.tminglei" %% "slick-pg" % 0.21.x ; + "org.postgresql" % "postgresql" % 42.7.x

app/migrations/drp-postgres/
├── V007__entity_name_unique_up.sql     # CREATE UNIQUE INDEX uq_entities_name ON entities(name);
└── V007__entity_name_unique_down.sql   # DROP INDEX uq_entities_name;

test/drp/asset/
├── domain/*Spec.scala                  # smart-constructor validation (no DB)
└── application/*Spec.scala             # services via in-memory repos (register / list / edit / duplicate / invalid-parent)
```

**Structure Decision**: Single modular-monolith web app. The feature is one module `app/drp/asset/` (owns the 4 tables + their screens + the read seam) plus a thin, self-contained `app/drp/shared/` that re-creates the common DRP primitives (ServiceResult, DomainError, Page, Clock, MonaPgProfile, base Twirl layout, root `DrpModule`) — mirroring the `app/todo` patterns **without importing from todo** (Constitution I). Cross-cutting foundation edits (`build.sbt`, `conf/application.conf`, `conf/routes`) wire PostgreSQL/slick-pg and the DRP module into the running app.

## Complexity Tracking

| Deviation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| `V007` adds `entities.name` UNIQUE — extends the v5 final-schema baseline (which omits it; `docs/migration_final_schema` §8) | The US makes global-unique entity name an explicit in-scope requirement + acceptance criterion (FR-002); Constitution V requires a **race-safe authoritative guard** (DB unique index), not just an app check | App-layer-only check rejected: not race-safe (V). Editing `V001` rejected: applied migrations are immutable — new behavior goes in a new version. |
| Foundation edits to shared `build.sbt` / `conf/application.conf` / `conf/routes` (outside `app/drp/asset/`) | First DRP feature must prove the Play+Twirl+PostgreSQL+slick-pg+migration stack end-to-end (US §1) and register the module/datasource/routes | Cannot run a PostgreSQL-backed module without the `drp` datasource, slick-pg deps, and module/route registration. Existing `slick.dbs.default` (todo) left intact. |

## Notes / Reconciliations (surfaced at the plan gate)

- **Spec ↔ authoritative schema**: spec FR-004 said assets unique `(entity_id, value)`; `V001`/schema enforce `(entity_id, asset_type, value) WHERE is_active=true`. Resolved in the schema's favour (CLAUDE.md §4); FR-004 reconciled in the spec. The US duplicate example (same entity + same value + `domain`) still rejects correctly.
- **entities.name uniqueness** satisfied via `V007` (above); FR-002 stands.
