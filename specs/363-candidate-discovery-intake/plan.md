# Implementation Plan: Candidate Discovery Intake & Staging

**Branch**: `features/363-Candidate-Discovery` | **Date**: 2026-06-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/363-candidate-discovery-intake/spec.md`

## Summary

Stand up the `app/drp/discovery/` module — the sole writer of `candidate_discoveries` — with synchronous Twirl intake (manual domain submission + permutation batch via a replaceable provider boundary), hostname normalisation, four-mode exclusion matching, deduplication, and a paginated list/detail view with status filtering. The `candidate_discoveries` table already exists (V002 migration). The only change to the asset module is adding `isActive: Boolean` to the `AssetView` read-model so the discovery service can gate permutation intake on active domain assets. No new migration, no worker, no queue.

## Technical Context

**Language/Version**: Scala 2.13.18 / Play Framework 2.9

**Primary Dependencies**: play-slick 5.2.0 (Slick 3.4.x); slick-pg 0.21.x (already added by feature 362); org.postgresql 42.7.x (already added); Guice; Twirl. **Guava** (`com.google.guava:guava`) for PSL-aware `InternetDomainName` — already on the Play transitive classpath, no new build.sbt entry required. `java.net.URI` + `java.net.IDN` (JDK stdlib) for hostname extraction and IDN→Punycode.

**Storage**: PostgreSQL (Docker `ghcr.io/pgmq/pg18-pgmq`, port 55432). `candidate_discoveries` and `candidates` tables exist via `V002__discovery_layer_up.sql` — **no new migration needed**.

**Testing**: ScalaTest + scalatestplus-play. Service and domain tests run against in-memory adapters (`InMemoryDiscoveryRepository`). Fake permutation provider (`FakePermutationProvider`) supplies a deterministic batch for automated tests. PostgreSQL/Slick path validated manually via `quickstart.md`.

**Target Platform**: Server-rendered Twirl SSR, single-JVM modular monolith.

**Project Type**: Web application (server-rendered) within a modular monolith.

**Performance Goals**: None numeric. All operations synchronous. Permutation batch writes bulk (no per-value DB call in a loop).

**Constraints**: Fully synchronous (no PGMQ/JobQueue/worker); no auth gate; no migration (V002 covers the schema); Single-Writer discipline (discovery owns `candidate_discoveries` only); cross-module reads through `AssetReadPort` exclusively.

**Scale/Scope**: Single-analyst, single-tenant, demo scale. One new module (`discovery`), one additive read-model change in `asset`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.* (Constitution v4.0.2)

| Principle | Status | Notes |
|---|---|---|
| I. Architecture Boundary | PASS | Code lives in `app/drp/discovery/` + one additive change to `app/drp/asset/application/ports/AssetReadPort.scala` and `AssetReadPortImpl.scala` (add `isActive` to `AssetView`). Domain layer stays pure: `NormalizedValue` uses only `java.net.URI` / `java.net.IDN` (JDK stdlib — no Play/Slick/JSON). PSL library (Guava `InternetDomainName`) is used in the `application` service layer only, not in `domain`. Cross-module reads go exclusively through `AssetReadPort` (already defined) — discovery never queries `assets`, `exclusions`, or `entities` tables directly. Single-Writer: `discovery` owns `candidate_discoveries`; `asset` remains the sole writer for its 4 tables. Pipeline direction: `discovery` depends on `asset` (one step downstream ✓). No `app/todo` dependency introduced. |
| II. Scope Boundary | PASS | Touches: `app/drp/discovery/**` (all new); `app/drp/asset/application/ports/AssetReadPort.scala` (add `isActive` to `AssetView`); `app/drp/asset/application/AssetReadPortImpl.scala` (map `a.isActive`); `conf/routes` (new `/drp/discoveries/...` and `/drp/permutation-intake/...` entries); `app/drp/asset/web/AssetController.scala` or entity detail view to add the permutation link (read-only navigation addition). No new migration. No changes to asset write behavior. No changes to `app/todo`. |
| III. Single Responsibility | PASS | `NormalizedValue` — normalization only; `ExclusionMatcher` — matching only; `DiscoveryIntakeService` — orchestrate intake only; `DiscoveryRepository` — persistence only; controllers are thin. |
| IV. Data Access Discipline | PASS | Discovery list **paginated** (`Page`/`PageRequest`) — growing set per Constitution IV. Permutation batch: collect all provider results first, deduplicate in-memory against a bulk-fetched set of existing normalized values for the entity, then bulk-insert unique ones — **no per-value DB call**. No large/binary content in discovery records. No loop-with-DB-call. |
| V. Abstraction Reflected in Comments | PASS | Ports, traits, and public service methods carry short WHAT comments. WHY comments only where non-obvious (e.g., the `invalid:` fallback invariant). |
| VI. Type-Conditional Behavior Preservation | PASS | `Type = Feature Implementation` (greenfield) — no behavior-preservation constraint beyond I & II. Asset module change is additive (new field on an existing case class with no existing consumers). |
| Additional Constraints — Platform & Stack | PASS | Per-module Guice `DiscoveryModule` composed via `DrpModule`; Twirl SSR (no React); `final case class` + smart constructors; closed sets as `sealed`; no IO in `domain`; repository port in `application`, Slick adapter in `infrastructure`; module owns its own Slick table definition (`CandidateDiscoveriesTable`); no PostgreSQL ENUM (existing TEXT + CHECK in V002); FK `ON DELETE RESTRICT` (existing); no new migration executed by the agent. |
| Dev-Workflow & Quality Gates | PASS | Each new port (`DiscoveryRepository`, `PermutationProvider`) ships with an in-memory / fake adapter; `ScalaTest` suite remains green; commit per approved phase. |

**Result: PASS.** No justified deviations to track.

## Project Structure

### Documentation (this feature)

```text
specs/363-candidate-discovery-intake/
├── plan.md              # This file
├── research.md          # Phase 0 — PSL library, normalisation, glob matching, permutation boundary
├── data-model.md        # Phase 1 — CandidateDiscovery, value types, enums, validation rules
├── quickstart.md        # Phase 1 — run + submit + verify validation guide
├── contracts/           # Phase 1 — ports, web routes
│   ├── ports.md
│   └── web-routes.md
└── tasks.md             # Phase 2 — /speckit-tasks output (NOT created here)
```

### Source Code (repository root)

```text
app/drp/
├── shared/                                     # unchanged (already exists)
│   └── boot/DrpModule.scala                    # EDIT: install DiscoveryModule
│
├── asset/                                      # minimal additive changes only
│   ├── application/ports/AssetReadPort.scala   # EDIT: add isActive to AssetView
│   ├── application/AssetReadPortImpl.scala     # EDIT: map a.isActive in resolveEntityWithAssets
│   └── web/AssetController.scala              # EDIT: add permutation-intake link on asset detail
│
└── discovery/                                  # NEW MODULE
    ├── domain/
    │   ├── DiscoveryId.scala                   # AnyVal wrapper (Long)
    │   ├── NormalizedValue.scala               # value object; smart ctor runs normalisation (stdlib only)
    │   ├── DiscoverySource.scala               # sealed: Manual | Permutation  (open enum + codec)
    │   ├── DnsStatus.scala                     # sealed: Pending | Active | Inactive | Error
    │   ├── SkipReason.scala                    # sealed: Whitelisted | InvalidFormat
    │   └── CandidateDiscovery.scala            # final case class + smart ctor
    ├── application/
    │   ├── ports/
    │   │   ├── DiscoveryRepository.scala       # save, get, findByEntityAndNormalized, listByEntity
    │   │   └── PermutationProvider.scala       # trait: generateLookAlikes(assetValue)
    │   ├── DiscoveryStatusFilter.scala         # sealed filter for list queries (Pending | Whitelisted | InvalidFormat)
    │   ├── DiscoveryIntakeService.scala        # trait: submitManual, requestPermutation
    │   └── DiscoveryIntakeServiceImpl.scala    # impl: normalise → dedupe → exclusion-check → save
    ├── infrastructure/
    │   ├── tables/
    │   │   └── CandidateDiscoveriesTable.scala # Slick table definition (MonaPgProfile)
    │   ├── slick/
    │   │   └── SlickDiscoveryRepository.scala  # real adapter (drp datasource)
    │   ├── inmemory/
    │   │   └── InMemoryDiscoveryRepository.scala  # test adapter
    │   ├── FakePermutationProvider.scala       # deterministic fake; fixed seed list for tests
    │   └── DiscoveryModule.scala               # Guice bindings (Slick vs InMemory)
    └── web/
        ├── DiscoveryController.scala           # list (with status filter), newForm, submit, detail
        ├── DiscoveryFormData.scala             # write model + Play Form
        ├── DiscoveryViewModel.scala            # flat read model for Twirl
        ├── PermutationIntakeController.scala   # newForm (pre-filled asset), submit
        ├── PermutationIntakeFormData.scala
        └── views/
            ├── discoveryForm.scala.html        # manual intake form
            ├── discoveryList.scala.html        # paginated list + status filter tabs
            ├── discoveryDetail.scala.html      # single-record detail
            └── permutationIntakeForm.scala.html # confirm permutation intake for an asset

conf/routes                                     # EDIT: add /drp/discoveries/** and /drp/permutation-intake/**

test/drp/discovery/
├── NormalizedValueSpec.scala                   # normalisation + invalid-fallback unit tests
├── ExclusionMatcherSpec.scala                  # all 4 match-type cases
├── DiscoveryIntakeServiceSpec.scala            # service tests against InMemoryDiscoveryRepository
└── FakePermutationProviderSpec.scala           # deterministic batch tests
```

## Complexity Tracking

No Constitution violations. No complexity deviations to justify.
