# Implementation Plan: Protected Entity & Asset Registration

**Branch**: `001-entity-asset-registration` | **Date**: 2026-06-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-entity-asset-registration/spec.md`

## Summary

Deliver the first DRP vertical slice: an analyst registers a protected **entity** (name + free-text type) and one or more **domain assets** under it, then views them on a server-rendered Twirl screen. Persistence targets the existing PostgreSQL `entities` / `assets` tables (already created by `V001__asset_layer_up.sql`). Technical approach: a self-contained `app/drp/asset/` module (domain → application(+ports) → infrastructure → web) wired to a **new, DRP-specific PostgreSQL Slick database** (`slick-pg` for JSONB), leaving the todo scaffold's SQL Server `default` connection untouched. No migration is authored or run by this slice.

## Technical Context

**Language/Version**: Scala 2.13.18

**Primary Dependencies**: Play Framework 2.9, play-slick 5.2, **slick-pg (to be added)**, **PostgreSQL JDBC driver (to be added)**, Guice, Twirl. (pac4j present in repo but unused in this slice.)

**Storage**: PostgreSQL (local Docker, port 55432) — existing `entities` / `assets` tables from `app/migrations/drp-postgres/V001__asset_layer_up.sql`. Accessed via a dedicated `slick.dbs.drp` connection reading `.env`.

**Testing**: ScalaTest + scalatestplus-play; service-level tests use in-memory repository adapters (reusing the todo scaffold's in-memory pattern), no live DB required. PostgreSQL path validated manually via `quickstart.md`.

**Target Platform**: JVM server (Play), local Docker PostgreSQL for dev.

**Project Type**: Web application — server-rendered (Twirl), modular monolith.

**Performance Goals**: Foundational CRUD; no numeric target. The listing screen MUST read entities and assets in bulk (no per-entity query) per Constitution V.

**Constraints**: Domain layer pure (no Play/Slick/HTTP/JSON/DB types); cross-module writes via ports + single-writer (asset module owns `entities`/`assets`); manual migrations only (app never auto-migrates); `assets.metadata` JSONB holds references/summaries only; `updated_at` is DB-trigger-maintained (not app-written).

**Scale/Scope**: Small — a handful of entities/assets for the Akbank demo; single analyst persona; no pagination, auth, or multi-tenant in this slice.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Notes |
|---|---|---|
| I. Architecture Boundary Conformance | PASS | All logic in `app/drp/asset/`; pure `domain`; `web`→`application`→`domain`, `infrastructure`→`application/ports`. No cross-module deps (asset is the root module). Single-writer: asset module owns `entities`/`assets`. Does not build on / reshape `app/todo/`. |
| II. Scope Boundary | PASS | In-scope files enumerated below (asset module + root wiring). No pipeline/downstream tables touched. |
| III. Single Responsibility | PASS | Service methods do one thing (register entity; register asset; list). |
| IV. Correctness Before Performance | PASS | No performance work; correctness-only slice. |
| V. Data Access Discipline | PASS | Listing uses 2 bulk queries (all entities + all assets) grouped in memory — no per-entity query. No worker/idempotency concerns. No large content in JSONB (references only). |
| VI. Abstraction Reflected in Comments | PASS | Short WHAT comments on traits/public methods; WHY only when non-obvious (CLAUDE.md §10). |
| VII. Type-Conditional Behavior Preservation | PASS | Type = Feature Implementation (greenfield); no behavior-preservation constraints beyond I/II. |
| Platform: Staging discipline | N/A | `entities`/`assets` are upstream of the pipeline; this slice writes no `candidates`. |
| Platform: Queue/Storage abstraction | N/A | No PGMQ/blob in this slice. |
| Platform: Explainable scoring | N/A | No scoring in this slice. |
| Platform: Schema invariants | PASS | Uses existing V001 schema as-is: no PG enum (TEXT+CHECK), FKs ON DELETE RESTRICT, no new FK; `metadata` references-only. |
| Platform: Migration discipline | PASS | No migration authored or executed; relies on already-written V001 applied manually. |
| Dev Workflow: tests pass | PASS | New ScalaTest specs added; existing suites keep passing. |

**Result: PASS — no violations. Complexity Tracking left empty.**

## Project Structure

### Documentation (this feature)

```text
specs/001-entity-asset-registration/
├── plan.md              # This file
├── research.md          # Phase 0 — technical decisions
├── data-model.md        # Phase 1 — entities, fields, validation
├── quickstart.md        # Phase 1 — runnable validation guide
├── contracts/
│   └── http-routes.md   # Phase 1 — route/form contract for the screens
└── checklists/
    └── requirements.md  # (from /speckit-specify)
```

### Source Code (repository root)

```text
app/drp/
├── shared/
│   └── infrastructure/
│       └── MonaPgProfile.scala            # slick-pg ExPostgresProfile + JSONB (shared DRP Slick profile)
└── asset/
    ├── domain/
    │   ├── Entity.scala                    # final case class + smart ctor; Either[DomainError, _]
    │   ├── Asset.scala                     # final case class + smart ctor
    │   ├── AssetType.scala                 # sealed ADT + codec (domain | subdomain)
    │   ├── AssetMetadata.scala             # references-only value object (optional ref fields)
    │   └── AssetDomainError.scala          # blank-name, blank-value, unknown-entity, duplicate-asset
    ├── application/
    │   ├── EntityService.scala / EntityServiceImpl.scala     # register + list entities
    │   ├── AssetService.scala / AssetServiceImpl.scala       # register asset under an entity
    │   ├── ports/
    │   │   ├── EntityRepository.scala      # port (interface)
    │   │   └── AssetRepository.scala       # port (interface)
    │   └── AssetModule.scala               # Guice: bind ports → Slick impls
    ├── infrastructure/
    │   ├── EntitiesTable.scala / EntityRow # Slick table mapping to `entities`
    │   ├── AssetsTable.scala / AssetRow    # Slick table mapping to `assets`
    │   ├── EntityMappers.scala             # Row ↔ domain
    │   ├── AssetMappers.scala              # Row ↔ domain (+ JSONB metadata codec)
    │   ├── SlickEntityRepository.scala      # @NamedDatabase("drp")
    │   ├── SlickAssetRepository.scala       # @NamedDatabase("drp")
    │   ├── InMemoryEntityRepository.scala   # test adapter
    │   └── InMemoryAssetRepository.scala    # test adapter
    └── web/
        ├── EntityController.scala          # POST create entity
        ├── AssetController.scala           # GET listing + POST create asset
        ├── EntityFormData.scala / AssetFormData.scala
        └── views/
            └── list.scala.html             # entities + their assets

# Root wiring (in scope — necessary infrastructure)
build.sbt                                   # + postgresql driver, + slick-pg
conf/application.conf                        # + slick.dbs.drp (PostgreSQL/.env); + enable AssetModule
conf/routes                                  # + asset-admin routes

# Tests
test/drp/asset/                              # ScalaTest specs (domain + service via in-memory repos)
```

**Structure Decision**: Single Play modular-monolith project. This feature lives entirely in the new `app/drp/asset/` module, with one shared Slick profile in `app/drp/shared/infrastructure/`. Persistence is wired to a **separate** `slick.dbs.drp` PostgreSQL connection so the todo scaffold's `slick.dbs.default` (SQL Server) is untouched. The todo scaffold's `BaseTables`/`SlickCrudSupport` are **not** reused (they assume an `isDeleted` soft-delete column and the default DB); only the conceptual patterns (port + Slick adapter, RowMapper, ServiceResult, per-module Guice module, in-memory test adapter) are mirrored.

## Complexity Tracking

> No Constitution Check violations. Nothing to justify.
