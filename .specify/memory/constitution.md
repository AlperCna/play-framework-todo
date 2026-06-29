<!--
Sync Impact Report

== Amendment 2026-06-29: 4.0.1 → 4.0.2 (PATCH) ==
- Development Workflow & Quality Gates: made the in-memory test-adapter expectation explicit — each new
  repository port ships with an in-memory test adapter (next to its Slick adapter) for DB-less service
  unit tests; adding a port without one must be justified. Also noted that DB-enforced invariants need
  integration tests (not covered by the in-memory backend). Codifies the existing convention (todo
  scaffold + specs/001-002, which already comply) → PATCH.
- Constitution-Aciklama.md Layer-1 (Development Workflow) updated to match.

== Amendment 2026-06-29: 4.0.0 → 4.0.1 (PATCH) ==
- Principle VI (Type-Conditional Behavior Preservation): added an explicit bullet for
  Type = Feature Implementation (greenfield baseline — no preservation constraint beyond I & II).
  Makes explicit what was already implied (and already in scope-reviewer's type lens + specs/001
  plan); no rule change → PATCH.
- Constitution-Aciklama.md Layer-1 (Principle VI) updated to match.

== Amendment 2026-06-29: 3.0.1 → 4.0.0 (MAJOR) ==
- Principle IV (Data Access Discipline): pagination strengthened from "large sets MUST paginate" to
  "pagination is the DEFAULT for any growing-set list read; a full/unbounded `SELECT *` read is allowed
  ONLY for a known small fixed-bound set, stated as the reason." Newly makes some previously-allowed
  unbounded reads non-compliant → backward-incompatible → MAJOR.
- Known retroactive conflicts (left as-is, point-in-time records): specs/001 loads all entities + assets
  at demo scale; the todo scaffold's unbounded `list*` methods. These become "exceptions needing a
  stated bound" or pagination candidates when they leave demo scale.
- scope-reviewer item 4 (Data access): synced — now flags unbounded/`SELECT *` reads of a growing set
  lacking pagination or a stated small/fixed-bound justification.

== Amendment 2026-06-29: 3.0.0 → 3.0.1 (PATCH) ==
- Principle IV (Data Access Discipline): added a directive that "bulk fetch" is NOT "unbounded" —
  large result sets are read with bounded pagination (`Page`/`PageRequest`), not loaded wholesale.
  Refines the existing bulk-fetch rule (no principle removed/redefined) → PATCH.
- scope-reviewer item 4 (Data access) already covers the data-discipline lens broadly; an explicit
  unbounded-load / pagination check can be added there later if it proves needed (not synced this run).

== Amendment 2026-06-29: 2.0.0 → 3.0.0 (MAJOR) ==
- Principle IV "Correctness Before Performance" REMOVED. Its performance floor already lives in
  Principle V (Data Access Discipline) and behavior-preservation in Principle VII; the only unique
  rule it added (do not bundle optimization into the correctness change) was judged not worth a
  standalone principle. Removing a principle is backward-incompatible → MAJOR bump.
- Renumbered: V→IV (Data Access Discipline), VI→V (Abstraction in Comments), VII→VI (Type-Conditional).
  Now 6 principles (I–VI).
- Dependent files synced: .claude/agents/scope-reviewer.md (dropped the "Correctness vs performance"
  check item; renumbered Data-access V→IV and comments VI→V; "I–VII"→"I–VI"). Constitution-Aciklama.md
  Layer-0/Layer-1 updated.
- NOT updated (historical point-in-time records, left as-is): specs/001-*, specs/002-* — their
  plan.md Constitution-Check tables and research/tasks/data-model "Constitution V" references still
  use the OLD numbering.

== Amendment 2026-06-29: 1.0.0 → 2.0.0 (MAJOR) ==
- Principle I, cross-module access rule REDEFINED (backward-incompatible): cross-module **read** no
  longer MAY use a direct Slick query — it now MUST go through the owner's `application/ports/` read
  port returning a typed read-model. Removing a previously-allowed path is a backward-incompatible
  redefinition → MAJOR bump.
- Rationale: a direct cross-module read forced either a duplicated Slick mapping in the reader or a
  silent schema coupling (an owner schema change breaks the reader). Routing reads through a read port
  keeps the table mapping single-sourced in the owner and stops domain/persistence types leaking across
  module boundaries. Resolves the prior constitution↔CLAUDE.md tension in favour of CLAUDE.md Lesson 4
  ("diğerleri port'tan okur").
- Dependent files synced: .claude/agents/scope-reviewer.md (check item 2). CLAUDE.md Lessons 2 & 4
  already aligned (no change). Constitution-Aciklama.md Layer-1 bullet updated.

== Initial ratification (1.0.0) ==
- Version change: 1.1.0 (biggerphish / .NET baseline) → 1.0.0 (Mona DRP initial ratification)
- Bump rationale: This file was REPURPOSED from another project's constitution (biggerphish, a
  brownfield .NET 8 / Clean Architecture codebase) to Mona DRP — a GREENFIELD Scala 2.13 / Play 2.9
  modular monolith. The format/structure is preserved; the content is rewritten to fit the DRP
  architecture and to stay consistent with CLAUDE.md. biggerphish's 1.x history does not apply, so the
  Mona DRP constitution starts at 1.0.0.
- Principles (6) — dependent tools (scope-reviewer.md, /speckit-analyze) reference "Constitution I–VI"
  by number, so renumbering here requires syncing them (done this run):
  - I   Architecture Boundary Conformance (was "Architecture Preservation") — modular monolith boundaries
  - II  Scope Boundary
  - III Single Responsibility
  - IV  Data Access Discipline (idempotent workers + no big content in JSONB/payload)
  - V   Abstraction Reflected in Comments (reconciled with CLAUDE.md §10 comment rule)
  - VI  Type-Conditional Behavior Preservation (aligned to the 6 us-input task Types)
- Sections: "Additional Constraints — Mona DRP Platform & Stack" (replaces ".NET / C# Conventions"),
  "Development Workflow & Quality Gates" (now: ScalaTest suites MUST keep passing), "Governance".
- Templates / agents checked for alignment:
  - .specify/templates/plan-template.md  ✅ "Constitution Check" gate is dynamic — auto-picks these principles; no new mandatory section introduced.
  - .specify/templates/spec-template.md  ✅ these principles add no new mandatory spec section.
  - .specify/templates/tasks-template.md ✅ no new principle-driven task category required.
  - .specify/templates/us-input-template.md ✅ already tech-agnostic; its 6 Type values back Principle VI.
- Deferred TODOs (still carry .NET / Clean Architecture wording — adapt next, this run only did constitution.md):
  - .claude/agents/scope-reviewer.md — rewrite the ".NET 8 / Clean Architecture" framing + check list to
    the Scala modular monolith (its I–VI references already map; only the prose/lens needs porting).
  - Re-verify spec/plan/tasks templates + .claude/skills/speckit-* prose for any residual .NET references.
-->

# Mona DRP Constitution

## Core Principles

### I. Architecture Boundary Conformance (MUST)

- DRP code lives under `app/drp/<module>/` with intra-module layers
  `domain / application (+ application/ports) / infrastructure / web / workers`. Dependencies point
  inward only: `web`/`workers` → `application` → `domain`, and `infrastructure` → `application/ports`.
  The `domain` layer MUST stay pure — no Play, Slick, HTTP, JSON, or DB types in it.
- The `web` layer (controllers + Twirl views) MUST stay thin: no business/decision logic and no direct data access — it always goes through an `application` service. A Twirl view is the **pure render of a typed view-model** passed by its controller; it MUST NOT call a service/repository, and domain/persistence types MUST NOT leak into it.
- Module dependency direction is one-way along the pipeline
  (`asset → discovery → candidate → crawl → analysis → risk → review → casework`; `shared`,
  `platform.storage`, `platform.queue` are horizontal helpers). No upward or cyclic module dependency.
- Cross-module access MUST go through the owning module's `application/ports/` interface for **both**
  directions. A **write / state change** calls a write port (the owner performs the write — **Single-Writer**:
  every table has exactly one writer module). A **read** calls a read port that returns a typed
  **read-model** (a projection) — never the owner's domain entity or Slick row. A module MUST NOT run a
  direct Slick query against another module's table. **In-module** access (a module to its own tables) is
  direct via its own Slick adapter.
- New code MUST reuse the established mechanisms (smart-constructor domain returning
  `Either[DomainError, _]`, `ServiceResult`, repository port + Slick adapter, per-module Guice module)
  rather than introducing a parallel/duplicate way of doing the same thing.
- `app/todo/` is a temporary pattern-reference scaffold only: DRP features MUST NOT be built on top of
  it, and it MUST NOT be reshaped (it will be removed once DRP stands on its own).

**Rationale**: In a greenfield modular monolith the boundaries ARE the long-term value. The first
cross-module shortcut or second pattern is how a monolith rots into a big ball of mud. (Mirrors
CLAUDE.md §2–3 and `docs/project_architecture/`.)

### II. Scope Boundary (MUST)

- Only the files and behaviors declared "in scope" by the active spec MAY be modified.
- If a change appears to require touching out-of-scope code, the agent MUST stop and ask (escalate)
  instead of proceeding.

**Rationale**: Scope creep is invisible without a hard rule. The produced diff must map 1:1 to the
spec's declared scope so a reviewer can verify it quickly.

### III. Single Responsibility (MUST)

- Each method MUST do exactly one thing. The metric is the number of responsibilities, not length —
  a long method that does one job is acceptable; a short method that does three is not.

**Rationale**: One responsibility per unit keeps changes local and review tractable.

### IV. Data Access Discipline (MUST)

- DB calls inside loops are FORBIDDEN. Fetch the required set in bulk, then process it in memory.
- Pagination is the DEFAULT for list reads: any query returning a set that grows with data MUST use
  `Page` / `PageRequest`. A full / unbounded read (`SELECT *` without a bound) is allowed ONLY when the
  set has a known small fixed upper bound (e.g., a lookup/enum table, one parent's bounded children),
  and that bound MUST be the stated reason — it is never the default.
- Read/write paths MUST explicitly address concurrency / race conditions. Pipeline workers MUST be
  idempotent — re-processing the same queue message MUST NOT create duplicates or double-promote a
  candidate.
- Large content (HTML, DOM, screenshot, OCR output, any binary) MUST NOT be embedded in a JSONB column
  or a queue message payload; it is stored in `blob_storage` and referenced via `storage_ref`.

**Rationale**: Per-iteration DB calls, unbounded in-memory loads, non-idempotent workers, and fat
JSONB/payloads are the silent performance and correctness defects of an async pipeline.

### V. Abstraction Reflected in Comments (MUST)

- A public abstraction (package / file / trait / public method) carries a short comment stating WHAT it
  does where the signature alone does not already make the abstraction obvious.
- Inline comments explain WHY only when the reason is non-obvious; never a line-by-line narration of
  HOW (consistent with CLAUDE.md §10 — do not add a comment unless the WHY is non-obvious).

**Rationale**: The intended abstraction should be readable without reverse-engineering the code; but a
comment that merely restates the code is itself noise.

### VI. Type-Conditional Behavior Preservation (MUST)

The us-input `Type` selects what "correct" means (the 6 supported values:
Feature Implementation, Feature Enhancement, Bug Fixing, Refactoring, Performance Optimization,
Code Understanding):

- `Type` = Feature Implementation: greenfield / new behavior — no behavior-preservation constraint
  beyond Principles I (reuse the established mechanisms; no parallel/duplicate) and II (scope). The bar
  is "the new behavior works as specified", not "existing behavior is unchanged".
- `Type` ∈ {Refactoring, Performance Optimization}: observable external behavior (inputs/outputs, public
  API **and port** contracts, side effects, data shapes) MUST be preserved byte-for-byte; new functional
  requirements and "opportunistic improvements" are FORBIDDEN.
- `Type` = Bug Fixing: only the faulty behavior changes; neighboring behavior MUST be preserved.
- `Type` = Feature Enhancement: only the declared delta (before→after) changes; the spec's "to preserve"
  list MUST stay intact.
- `Type` = Code Understanding: NO code is produced (implement is not run); output is analysis only.

**Rationale**: The definition of "correct" depends on the task type; behavior-preserving types are
exactly where an agent is most tempted to drift.

## Additional Constraints — Mona DRP Platform & Stack (MUST)

**Stack & code conventions**

- Scala **2.13.18** / Play **2.9**; Guice DI — each DRP module exposes its own Guice `Module`, composed
  at a root `DrpModule`. Views are Twirl server-rendered; **no React / SPA**.
- Domain: immutable `final case class` + smart constructors; validation returns `Either[DomainError, _]`.
  Closed sets are `sealed trait` + `final case class` / `case object` ADTs — `sealed` so the compiler
  enforces exhaustive matching. Model so that **illegal states are unrepresentable**: constrain values
  through smart constructors and meaningful wrapper types, not bare `String` / `Int`. Services return
  `Future[Either[DomainError, _]]` / `ServiceResult`. No IO in `domain`.
- Persistence: PostgreSQL + `slick-pg`. The repository **port** lives in `<module>/application`, the Slick
  adapter in `<module>/infrastructure`; each module owns its own Slick table definitions (DRP does NOT
  accumulate a single growing `Tables` facade).

**Platform & pipeline invariants**

- **Staging discipline**: every input (manual, permutation, external feed) MUST enter through the
  `candidate_discoveries` staging table; nothing is written directly to `candidates`. Promotion happens
  only after exclusion + DNS/HTTP validation and is tracked via `candidates.discovery_id` (NOT NULL).
- **Queue / storage abstraction**: business code MUST depend on the `JobQueue` and `StorageService`
  interfaces, never on PGMQ functions or `blob_storage` SQL directly. Queue messages carry only
  `target_type` / `target_id` / `job_type` + small params — never HTML/DOM/screenshot/binary.
- **Explainable risk scoring**: the decision engine MUST be explainable rule-based scoring; an LLM MUST
  NOT be the decision-maker (LLM is at most an optional summary, MVP Plus). `risk_scores` and
  `rule_results` MUST be written in a single transaction — a total without its rule breakdown is never
  observable.
- **Schema invariants**: no PostgreSQL ENUM (critical lifecycle fields are TEXT + CHECK, with a code-side
  sealed/open enum + codec); all foreign keys are `ON DELETE RESTRICT` (no cascade); score fields are
  `NUMERIC(5,4)` + `CHECK 0..1`. `candidates.status` has no `whitelisted`; `evidence_files` has no `case_id`.
- **Migration discipline**: schema/data changes are hand-written, versioned SQL in
  `app/migrations/drp-postgres/` (`V00X__<layer>_up.sql` / `_down.sql`, down in reverse-FK order) — NOT
  Play Evolutions / Flyway, and the app NEVER auto-migrates on startup. The agent MAY write a migration
  file but MUST NOT execute it (or any migration) against a database; applying it is a manual human step
  (`scripts/migrate_drp_up.*`).

**Rationale**: These are the invariants that keep the pipeline swappable (PGMQ→Kafka, blob→S3), auditable
(explainable score + provenance chain), and safe (single staging gate, restrict-deletes, manual
migrations). Operational detail — build/run commands, folder map, exact table columns — lives in CLAUDE.md
and `docs/`, not here.

## Development Workflow & Quality Gates

- Every feature flows through the SDD pipeline: specify → clarify → plan → tasks → analyze → implement,
  with **human approval at each gate** (mirrors CLAUDE.md §10: explore → propose plan → get approval →
  implement; no big one-shot refactor).
- `/speckit-analyze` MUST be run before `/speckit-implement`. CRITICAL findings (a violation of any MUST
  here) BLOCK implementation until resolved — by fixing the spec/plan/tasks, never by diluting the
  constitution.
- A git checkpoint (commit) MUST be made after each approved phase; work proceeds module-by-module with
  atomic, independent commits.
- Validation: the existing **ScalaTest** suites under `test/` MUST keep passing at every step. Each new
  repository **port** ships with an **in-memory test adapter** (next to its Slick adapter) so the owning
  service is unit-testable without a live DB — the established `InMemory*Repository` pattern (todo
  scaffold); adding a port without one must be justified. New behavior is validated by human review +
  the feature's `quickstart.md`. This rigor will tighten later (characterization tests for
  behavior-preserving types first; DB-enforced invariants — constraints, transactions, concurrency —
  are NOT covered by the in-memory backend and need integration tests).

## Governance

- This constitution supersedes ad-hoc practices. Amendments require: a documented change, a version
  bump, and synchronization of dependent templates and agents (notably `.claude/agents/scope-reviewer.md`).
- Versioning policy: **MAJOR** = backward-incompatible principle removal/redefinition; **MINOR** = a new
  principle or section added; **PATCH** = clarifications and wording.
- Compliance: `/speckit-analyze` and the `scope-reviewer` subagent verify adherence (both reference
  principles I–VI by number — keep them in sync when renumbering). Any complexity that bends a principle
  MUST be justified in `plan.md`'s Complexity Tracking.

**Version**: 4.0.2 | **Ratified**: 2026-06-26 | **Last Amended**: 2026-06-29
