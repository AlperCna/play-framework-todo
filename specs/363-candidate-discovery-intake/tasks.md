# Tasks: Candidate Discovery Intake & Staging

**Input**: Design documents from `specs/363-candidate-discovery-intake/`

**Prerequisites**: [plan.md](./plan.md) · [spec.md](./spec.md) · [research.md](./research.md) · [data-model.md](./data-model.md) · [contracts/ports.md](./contracts/ports.md) · [contracts/web-routes.md](./contracts/web-routes.md) · [quickstart.md](./quickstart.md)

**Tests**: Required per constitution Dev-Workflow rule. Each new port (`DiscoveryRepository`, `PermutationProvider`) ships with an in-memory / fake adapter. Service-level ScalaTest specs cover all intake paths. NormalizedValue and ExclusionMatcher get dedicated unit specs.

---

## Phase 1: Setup

**Purpose**: Create the discovery module skeleton and wire it into the DRP root.

- [X] T001 Create discovery module directory tree: `app/drp/discovery/{domain,application/ports,infrastructure/tables,infrastructure/slick,infrastructure/inmemory,web/views}/`
- [X] T002 Create empty Guice module skeleton `app/drp/discovery/infrastructure/DiscoveryModule.scala` + install it in `app/drp/shared/boot/DrpModule.scala`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: All domain types, ports, in-memory adapters, and shared infrastructure that every user story depends on. No user story phase can begin until this is complete.

**⚠️ CRITICAL**: Complete before starting any Phase 3+ work.

- [X] T003 [P] Edit `app/drp/asset/application/ports/AssetReadPort.scala` — add `isActive: Boolean` to `AssetView`; edit `app/drp/asset/application/AssetReadPortImpl.scala` — map `a.isActive` in the `assets.map(...)` call of `resolveEntityWithAssets`
- [X] T004 [P] Edit `app/drp/shared/domain/DomainError.scala` — add 5 new cases: `EmptyDiscoveryValue`, `AssetEntityMismatch(assetId, entityId)`, `AssetNotDomainType(assetId)`, `AssetNotActive(assetId)`, `PermutationProviderFailure(message)`
- [X] T005 [P] Create `app/drp/discovery/domain/DiscoveryId.scala` — `final case class DiscoveryId(value: Long) extends AnyVal` (0 = unsaved)
- [X] T006 [P] Create `app/drp/discovery/domain/DiscoverySource.scala` — `sealed trait DiscoverySource { def code: String }` with `Manual` ("manual") and `Permutation` ("permutation") + `fromCode`/`toCode`
- [X] T007 [P] Create `app/drp/discovery/domain/DnsStatus.scala` — `sealed trait DnsStatus { def code: String }` with `Pending`/`Active`/`Inactive`/`Error` + `fromCode`/`toCode`; default on intake = `Pending`
- [X] T008 [P] Create `app/drp/discovery/domain/SkipReason.scala` — `sealed trait SkipReason { def code: String }` with `Whitelisted` ("whitelisted") and `InvalidFormat` ("invalid_format") + `fromCode`/`toCode`; `fromCode` handles "duplicate" defensively
- [X] T009 [P] Create `app/drp/discovery/domain/NormalizedValue.scala` — value object wrapping `String`; factory `NormalizedValue.from(raw: String): NormalizedValue` applies the full normalisation pipeline (trim → `java.net.URI` hostname extraction → `java.net.IDN.toASCII` → lowercase → remove trailing dot → preserve `www`); malformed non-empty input → `"invalid:" + raw.trim.toLowerCase.replaceAll("\\s+", " ")`; never throws
- [X] T010 Create `app/drp/discovery/domain/CandidateDiscovery.scala` — `final case class CandidateDiscovery(id, entityId, assetId, value, normalizedValue, source, dnsStatus, skipReason, failedCheckCount, httpStatusCode, lastCheckedAt, nextCheckAt, createdAt, updatedAt)` + companion smart ctor `intake(entityId, assetId, rawValue, source)` that builds the record with `dnsStatus=Pending`, `skipReason=None`, `failedCheckCount=0`, all check fields `None` (depends on T005–T009)
- [X] T011 [P] Create `app/drp/discovery/application/DiscoveryStatusFilter.scala` — `sealed trait DiscoveryStatusFilter` with `PendingValidation` / `Whitelisted` / `InvalidFormat`; document SQL predicates for each
- [X] T012 [P] Create `app/drp/discovery/application/ports/DiscoveryRepository.scala` — port trait with `save`, `get`, `findByEntityAndNormalized`, `listNormalizedValuesByEntity`, `listByEntity(entityId, statusFilter: Option[DiscoveryStatusFilter], page: PageRequest)` (see [contracts/ports.md](./contracts/ports.md))
- [X] T013 [P] Create `app/drp/discovery/application/ports/PermutationProvider.scala` — port trait with `generateLookAlikes(assetValue: String): Future[Seq[String]]` (see [contracts/ports.md](./contracts/ports.md))
- [X] T014 Create `app/drp/discovery/infrastructure/tables/CandidateDiscoveriesTable.scala` — Slick table definition on `MonaPgProfile`; maps all columns including `source`/`dns_status`/`skip_reason` as TEXT via coded sealed types; `asset_id` nullable (depends on T005–T010)
- [X] T015 Create `app/drp/discovery/infrastructure/inmemory/InMemoryDiscoveryRepository.scala` — in-memory adapter backed by `mutable.Map[DiscoveryId, CandidateDiscovery]`; enforces `(entityId, normalizedValue.value)` uniqueness in `save`; implements `listNormalizedValuesByEntity` and `listByEntity` with status filter; auto-assigns incrementing ids (depends on T012, T010, T011)
- [X] T016 [P] Create `app/drp/discovery/infrastructure/FakePermutationProvider.scala` — `FakePermutationProvider(seed: Seq[String])` returns the seed as `Future.successful(seed)`; companion with a `defaultSeed` containing a valid domain, a malformed value, and a domain that matches a predictable exclusion (depends on T013)
- [X] T017 [P] Create `test/drp/discovery/NormalizedValueSpec.scala` — unit tests: URL→hostname, uppercase→lowercase, trailing-dot removal, www preservation, IDN→Punycode, credentials/port/path/query/fragment discarded, non-empty malformed → `invalid:` prefix, same malformed value resubmitted → same normalized form (depends on T009)
- [X] T018 Create `app/drp/discovery/application/ExclusionMatcher.scala` — `object ExclusionMatcher { def matches(discovery: NormalizedValue, exclusions: Seq[ExclusionView]): Option[SkipReason] }`; implements all 4 match types: `exact` (string equality), `registrable_domain` (Guava `InternetDomainName.topPrivateDomain`), `subdomain_of` (`endsWith("." + excl)`, exclusion itself excluded), `pattern` (glob → anchored regex, `*`→`.*`, `?`→`.`); returns `Some(Whitelisted)` on first match, `None` otherwise (depends on T008, T009)

**Checkpoint**: Domain model, ports, in-memory adapters, and normalisation + matching logic are all in place. User story work can begin.

---

## Phase 3: User Story 1 — Manual Suspicious Domain Submission (Priority: P1) 🎯 MVP

**Goal**: An analyst can submit a suspicious domain manually, see the resulting staging record with correct normalisation and exclusion status, and view the record in a basic list and detail view.

**Independent Test**: Create an entity + active domain asset + active `exact` exclusion. Submit a valid domain → verify `pending` record with correct normalised value. Submit the excluded domain → verify `whitelisted` record. Submit a malformed value → verify `invalid_format` record. Submit the same domain again → verify no second record. Open the list → all three records visible.

### Tests for User Story 1 ⚠️

- [X] T019 [US1] Create `test/drp/discovery/ExclusionMatcherSpec.scala` — unit tests for all 4 match types (`exact`, `registrable_domain`, `subdomain_of`, `pattern`); include non-matching cases to verify zero false positives; test `subdomain_of` does NOT match the exclusion host itself (depends on T018, T017)
- [X] T020 [US1] Create `test/drp/discovery/DiscoveryIntakeServiceSpec.scala` — service unit tests using `InMemoryDiscoveryRepository` + `FakePermutationProvider`: valid manual submission, exclusion match → whitelisted, malformed → invalid_format, duplicate → no second record, blank → domain error, cross-entity asset → domain error, asset `None` → null asset_id (depends on T015, T016, T018)

### Implementation for User Story 1

- [X] T021 [US1] Create `app/drp/discovery/application/DiscoveryIntakeService.scala` — trait with `submitManual(entityId, assetId, rawValue)`, `listDiscoveries(entityId, statusFilter, page)`, `getDiscovery(id)` (see [contracts/ports.md](./contracts/ports.md))
- [X] T022 [US1] Create `app/drp/discovery/application/DiscoveryIntakeServiceImpl.scala` — implement `submitManual`: (1) resolve entity via `AssetReadPort.resolveEntityWithAssets` → `EntityNotFound` if absent; (2) if assetId provided, validate asset belongs to entity → `AssetEntityMismatch` if not; (3) reject blank raw value → `EmptyDiscoveryValue`; (4) `NormalizedValue.from(raw)` → `CandidateDiscovery.intake`; (5) `ExclusionMatcher.matches(normalized, activeExclusions)` → set `skipReason`; (6) `findByEntityAndNormalized` → return existing if duplicate; (7) `repo.save(discovery)`. Implement `listDiscoveries` and `getDiscovery`. Inject `AssetReadPort`, `DiscoveryRepository`, `ExclusionMatcher`-compatible logic (depends on T018, T021, T012, T011)
- [X] T023 [US1] Create `app/drp/discovery/infrastructure/slick/SlickDiscoveryRepository.scala` — Slick adapter on `drp` datasource; implement all 5 port methods; `listByEntity` applies `statusFilter` SQL predicates per `DiscoveryStatusFilter` docs; `listNormalizedValuesByEntity` returns `Set[String]` via a single query (depends on T014, T012, T011)
- [X] T024 [US1] Update `app/drp/discovery/infrastructure/DiscoveryModule.scala` — bind `DiscoveryIntakeService → DiscoveryIntakeServiceImpl`; bind `DiscoveryRepository → SlickDiscoveryRepository` (or `InMemoryDiscoveryRepository` in Test/inMemory mode); bind `PermutationProvider → FakePermutationProvider`; bind `AssetReadPort` via install of `AssetModule` (already bound)
- [X] T025 [P] [US1] Create `app/drp/discovery/web/DiscoveryFormData.scala` — `DiscoveryFormData(entityId, assetId, value)` + Play `Form` with `nonEmptyText` constraint on `value`; create `app/drp/discovery/web/DiscoveryViewModel.scala` — flat projection with `id`, `entityId`, `entityName`, `assetId`, `assetValue`, `value`, `normalizedValue`, `source`, `dnsStatus`, `skipReason`, `statusLabel`, `createdAt` (formatted string)
- [X] T026 [US1] Create `app/drp/discovery/web/DiscoveryController.scala` — `MessagesAbstractController`; `newForm(entityId)` — load all entities for selector, pre-select entityId if provided, load assets for that entity; `submit` — `bindFromRequest.fold(bad → re-render form, ok → service.submitManual → Redirect/BadRequest)`; `list(entityId, status, page)` — load discoveries with basic (no filter yet) pagination, map to `DiscoveryViewModel`; `detail(id)` — load and map single record (depends on T022, T025)
- [X] T027 [P] [US1] Create Twirl views: `app/drp/discovery/web/views/discoveryForm.scala.html` — manual intake form with entity selector, optional asset selector, value input field, CSRF token, global error display; `app/drp/discovery/web/views/discoveryList.scala.html` — paginated list table showing all `DiscoveryViewModel` fields, status label column (no filter UI yet — US3 adds it); `app/drp/discovery/web/views/discoveryDetail.scala.html` — all fields displayed including entity name, optional asset, all status fields
- [X] T028 [US1] Add discovery route block to `conf/routes` (see [contracts/web-routes.md](./contracts/web-routes.md)); add i18n message keys for all `DomainError` codes added in T004 to `conf/messages`

**Checkpoint**: Manual intake is fully functional. Analyst can submit, view list, and view detail. All US1 ScalaTest specs pass. Quickstart Scenarios 1–6 are verifiable.

---

## Phase 4: User Story 2 — Permutation Intake for a Domain Asset (Priority: P2)

**Goal**: An analyst can trigger permutation intake for an active domain asset from the asset detail page. The system stages all unique provider results through the same normalisation + exclusion pipeline. Provider failure leaves no partial batch.

**Independent Test**: With `FakePermutationProvider.defaultSeed` configured, trigger permutation intake for an active domain asset. Verify unique valid results staged as `source=permutation`, duplicates silently skipped, exclusion-matched values staged as whitelisted, provider failure returns error with no records written.

### Tests for User Story 2 ⚠️

- [X] T029 [US2] Extend `test/drp/discovery/DiscoveryIntakeServiceSpec.scala` with permutation intake scenarios: provider returns batch → unique values staged, duplicates skipped; provider returns empty → no records, no error; provider fails → error returned, no partial batch written; inactive asset → `AssetNotActive` error; non-domain asset → `AssetNotDomainType` error; unknown asset → `AssetNotFound` error (depends on T020)

### Implementation for User Story 2

- [X] T030 [US2] Add `requestPermutation(assetId: Long): ServiceResult[Int]` to `DiscoveryIntakeServiceImpl.scala`: (1) `resolveEntityWithAssets` to find entity + verify asset exists, belongs to entity, is active, and is domain type → appropriate errors; (2) call `permutationProvider.generateLookAlikes(asset.value)` — recover `Failed` future → `PermutationProviderFailure`; (3) `listNormalizedValuesByEntity` to fetch existing normalized values in bulk; (4) in-memory deduplicate provider results; (5) for each unique value: `CandidateDiscovery.intake(entityId, Some(assetId), raw, Permutation)` → `ExclusionMatcher.matches` → set skipReason → `repo.save`; (6) return count of newly staged records (depends on T022, T013, T012)
- [X] T031 [US2] Create `app/drp/discovery/web/PermutationIntakeController.scala` — `newForm(assetId)`: load asset view, verify active domain type, render confirmation form; `submit`: parse `assetId` from form, call `service.requestPermutation`, on success redirect to discovery list for the entity with a flash message showing count, on error re-render form with global error; create `app/drp/discovery/web/PermutationIntakeFormData.scala` — `PermutationIntakeFormData(assetId: Long)` + Play `Form` (depends on T030, T025)
- [X] T032 [P] [US2] Create `app/drp/discovery/web/views/permutationIntakeForm.scala.html` — confirmation form showing asset domain value and entity name, CSRF token, submit and cancel buttons
- [X] T033 [US2] Add permutation-intake route block to `conf/routes` (GET `/drp/permutation-intake/new` + POST `/drp/permutation-intake`) per [contracts/web-routes.md](./contracts/web-routes.md)
- [X] T034 [US2] Edit `app/drp/asset/web/views/assetView.scala.html` (or `AssetController`) — add "Request permutation intake" link rendered only when `asset.assetType == "domain"` and `asset.isActive == true`; link target: `/drp/permutation-intake/new?assetId=<id>`

**Checkpoint**: Permutation intake works end-to-end. Quickstart Scenarios 7 and the provider-failure path are verifiable.

---

## Phase 5: User Story 3 — Discovery List Status Filter (Priority: P3)

**Goal**: The analyst can filter the discovery list by status category (Pending Validation / Whitelisted / Invalid Format) to quickly focus on records of interest.

**Independent Test**: With at least one record in each status category, open the list with `?status=pending`, `?status=whitelisted`, `?status=invalid_format`, and no filter. Confirm each view shows only the expected subset (or all records for no filter).

### Implementation for User Story 3

- [X] T035 [US3] Update `app/drp/discovery/web/DiscoveryController.scala` — `list(entityId, status, page)`: parse `status` query param into `Option[DiscoveryStatusFilter]` (`"pending"` → `PendingValidation`, `"whitelisted"` → `Whitelisted`, `"invalid_format"` → `InvalidFormat`, absent/unknown → `None`); pass filter to `service.listDiscoveries`
- [X] T036 [US3] Update `app/drp/discovery/web/views/discoveryList.scala.html` — add status filter tab row/links: "All" · "Pending Validation" · "Whitelisted" · "Invalid Format"; highlight the active tab based on the current `status` query param; ensure each category uses a visually distinct colour or badge so the three types are "clearly distinguishable at a glance" per SC-002
- [X] T037 [US3] Update `test/drp/discovery/DiscoveryIntakeServiceSpec.scala` — add `listDiscoveries` filter tests: given records of all three types, verify each filter returns only the expected subset; verify `None` filter returns all

**Checkpoint**: Status filter is functional. Quickstart Scenario 8 is fully verifiable.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T038 [P] Verify i18n completeness — check every `DomainError.code` added in T004 (and all pre-existing codes used by discovery controllers) has a corresponding entry in `conf/messages`; add any missing keys
- [X] T039 Run `sbt test` from repo root — confirm all ScalaTest suites (`NormalizedValueSpec`, `ExclusionMatcherSpec`, `DiscoveryIntakeServiceSpec`) pass and no existing test regressions
- [X] T040 Run quickstart.md manual validation scenarios 1–8 against the running application; confirm all expected outcomes match

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1. **BLOCKS all user stories.** T003–T018 can largely run in parallel within the phase (see [P] markers).
- **Phase 3 (US1)**: Depends on Phase 2. T019–T020 (tests) can be written while T021–T028 (impl) proceeds; within US1, T022 depends on T021; T026 depends on T022 and T025; T027 can be written alongside T026.
- **Phase 4 (US2)**: Depends on Phase 2 + Phase 3 (uses service impl and intake pipeline from T022). T029–T034 proceed in order; T032 and T034 are parallel.
- **Phase 5 (US3)**: Depends on Phase 3 (controller + list view exist). T035 → T036 → T037.
- **Phase 6 (Polish)**: Depends on Phase 5 completion.

### User Story Dependencies

- **US1 (P1)**: Unblocked after Phase 2 — no inter-story dependencies.
- **US2 (P2)**: Depends on US1 service impl (T022) — `requestPermutation` extends `DiscoveryIntakeServiceImpl`.
- **US3 (P3)**: Depends on US1 controller + list view (T026, T027) — adds filter on top of the existing list.

### Within-Phase Parallel Opportunities

**Phase 2**:
```
T003 T004 T005 T006 T007 T008 T009 T011 T012 T013 T016 T017  ← all parallel
T010 (after T005–T009)
T014 (after T010)
T015 (after T012, T010)
T018 (after T008, T009)
```

**Phase 3**:
```
T019 T020 T021 T025  ← parallel (tests + trait + view-model)
T022 (after T021, T018)
T023 (after T014, T012)
T024 (after T022, T023)
T026 (after T022, T025)
T027 (parallel with T026 — different files)
T028 (after T026)
```

**Phase 4**:
```
T029 (parallel — extends tests)
T030 (after T022 — extends service impl)
T031 (after T030)
T032 T034 (parallel — different files)
T033 (after T031)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 + Phase 2 → Foundation ready.
2. Complete Phase 3 (US1) → Manual intake with basic list/detail fully working.
3. **STOP and VALIDATE**: Run `sbt test` + quickstart Scenarios 1–6.
4. Demo: analyst can submit suspicious domains and review them.

### Incremental Delivery

1. Foundation → US1 (Manual Intake + List/Detail) → Demo.
2. US2 (Permutation Intake) → Demo.
3. US3 (Status Filter) → Demo.
4. Polish → Ship.

---

## Notes

- `[P]` = different files, no blocking dependency — safe to parallelise.
- `[USn]` maps each task to its user story for traceability.
- Tests tasks are listed before their corresponding implementation tasks to maintain the TDD intent (write spec first, verify it fails, then implement).
- Each phase ends at a checkpoint that is independently runnable and testable.
- `sbt test` must exit 0 after every phase — do not advance until green.
- Never execute migrations — agent writes SQL files only; human applies them via `scripts/migrate_drp_up.*`.
- No code is written to `candidates` table — `candidate_discoveries` only.
