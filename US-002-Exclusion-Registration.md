# User Story: Exclusion (Allowlist) Registration (Foundational Vertical Slice)

**US ID**: [DevOps work item no.]
**Type**: Feature Implementation
**Status**: Draft
**Summary (one sentence)**: An analyst can register and view exclusions (allowlist entries — owned-but-unmonitored or legitimate third-party domains/URLs) under a protected entity, persisted in PostgreSQL and shown on a server-rendered screen, so the future discovery pipeline has a declared allowlist to consult.

---

## 1. Problem / Motivation (WHY) *(mandatory)*

- The §0 setup of the pipeline has three foundational pieces: the protected entity, its assets, and its exclusions. The entity + asset layer already exists (US-001); exclusions are the missing third piece. Exclusions are the allowlist that the candidate-discovery stage reads **first**, before any DNS/HTTP check, so that owned-but-unmonitored domains (e.g., `akbankdirekt.com`) and legitimate third-party domains are never promoted into the analysis pipeline or flagged as threats.
- The concrete pain: today there is no place to declare "this domain is ours / legitimate — never flag it." Without it, the future discovery pipeline would have no allowlist to consult and would produce false positives, or the allowlist would end up hard-coded.
- Once solved, the protected-entity setup surface is complete (entity + asset + exclusion), and the downstream discovery US can rely on a real, queryable `exclusions` table instead of a placeholder. The agent should treat "complete a clean, demoable setup surface" as the tie-breaker for any ambiguity below.

## 2. Scope *(mandatory)*

- **In scope**:
  - Exclusion registration capability: under an existing entity, create an exclusion with `value`, `match_type`, `reason`, and `is_active` (defaulting to true), and store it.
  - A server-rendered (Twirl) screen that lists exclusions per entity.
  - Relying on the **already-written** asset-layer migration (`V001`, which includes `exclusions`) — this slice authors no new migration and runs none (migrations are applied manually as a human/setup step).
  - The minimal wiring to host exclusions inside the existing **asset module** (`app/drp/asset/`), reusing US-001's entity lookup.
- **Out of scope** (strictly off-limits — MUST NOT be built or touched):
  - **Applying** exclusions during candidate discovery / matching (the `skip_reason = "whitelisted"` skip logic belongs to the `candidate_discoveries` US) — this slice only **registers and views** them.
  - Global exclusions (`entity_id` null) — `entity_id` is required in this slice; the column stays nullable for future use.
  - Edit / delete / deactivation of exclusions (this slice is **create + view** only).
  - Real authentication / RBAC and any real current-user source for `created_by`.
  - `candidate_discoveries`, `candidates`, and everything downstream; PGMQ / JobQueue; `blob_storage`.
  - The US-001 `entities` / `assets` capability — reused, not modified.
  - Any use of DB-level `enum` types.

## 3. Type-Specific Core *(mandatory — Feature Implementation block)*

**▸ Type ∈ {Feature Implementation, Feature Enhancement, Bug Fixing}**
- **Expected behavior**:
  - Under an existing entity, the analyst creates an exclusion by providing `value` (e.g., `"akbankdirekt.com"`), `match_type` (e.g., `"exact"`), and `reason` (e.g., `"owned_unmonitored"`); the row is stored with `is_active = true` and audit stamps.
  - A server-rendered page lists all exclusions for an entity.
- **Beyond the happy path** (known edge / boundary cases — visible, non-crashing):
  - Blank `value` → rejected with a visible validation message; nothing persisted.
  - Exclusion under a non-existent entity → rejected; nothing persisted.
  - `match_type` set to another **allowed** value (`registrable_domain` / `subdomain_of` / `pattern`) → accepted; a value outside the four allowed ones is rejected by the DB CHECK (`match_type` is a closed set).
  - `reason` set to any value (e.g., `"third_party_legit"`) → accepted (open field, no DB CHECK).
  - Registering an active duplicate `(entity_id, value, match_type)` → rejected (DB-guarded) with a visible message.
  - Entity with zero exclusions → valid; shown with an empty list.
- **To preserve**:
  - The US-001 entity/asset behavior MUST NOT be altered; this slice reuses the existing entity lookup (the asset module's `EntityRepository.existsById`) rather than duplicating it. Invariants: (1) conformance to the v5 data-model column contract (`docs/migration_final_schema/migration_final_schema.md`), and (2) the modular-monolith boundary — exclusions belong to the **asset module** (which already owns `entities`/`assets`); their logic must not leak into unrelated modules.
- *(Bug Fixing only)*: N/A

## 4. Acceptance Criteria / Done *(mandatory)*

- [ ] On a fresh DB, manually applying the already-written asset-layer migration (`V001__asset_layer_up.sql`, which creates `exclusions` alongside `entities`/`assets`) via the migration/setup script — the app never auto-migrates — makes the `exclusions` table available with its v5 columns; this slice authors no migration and runs none.
- [ ] Creating an exclusion `"akbankdirekt.com"` (`match_type = "exact"`, `reason = "owned_unmonitored"`) under an existing entity persists a row with the correct `entity_id`, `value`, `match_type`, `reason`, `is_active = true`, and audit stamps; it appears on the listing screen.
- [ ] Submitting an empty `value` shows a validation error and persists nothing.
- [ ] Submitting an exclusion under a non-existent entity is rejected and persists nothing.
- [ ] `match_type` and `reason` are stored as strings (no DB enum); `reason = "third_party_legit"` (open field) is accepted as-is, and `match_type` accepts any of the four CHECK-allowed values while rejecting any other.
- [ ] An entity with no exclusions renders an empty list (not an error).

---

## 5. Assumptions & Preconditions *(if any; else "None")*

- US-001's `entities` / `assets` exist; an entity can be referenced when creating an exclusion.
- The table-creation SQL for `exclusions` is already written and available to apply.
- A PostgreSQL instance is reachable, and the Play application boots in the current skeleton.
- No authentication layer exists yet; the screen is reachable without login in this slice.
- The **v5 data-model** — `docs/migration_final_schema/migration_final_schema.md` — is the authoritative source for column names, types, nullability, defaults, and constraints (incl. the `match_type` CHECK and the `created_by` default).

## 6. Constraints / Externally Imposed Decisions *(if any; else "None")*

- Backend is **Scala Play Framework**; the UI is **server-rendered Twirl** (no React). *(architecture decision)*
- Database is **PostgreSQL**; architecture is a **modular monolith**. *(architecture decision)*
- `match_type` and `reason` are stored as **DB strings** (not DB-level enums), via a code-side codec. `match_type` is a **closed set** backed by a DB CHECK (`exact` / `registrable_domain` / `subdomain_of` / `pattern`) → a sealed enum; `reason` is **open** (no CHECK) → an open enum. *(data-model design rule)*
- Audit convention: mutable tables carry `created_at` + `updated_at` (`updated_at` is DB-trigger-maintained). `created_by` is **NOT NULL with DB default `'system'`** (v5 schema) — there is no current-user seam yet, so this slice relies on that default; it is **not** nullable, and no real auth is wired.
- `entity_id` is nullable in the schema (reserved for future global exclusions) but **required** in this slice.
- `value` stores the exclusion target as-is (e.g., `"akbankdirekt.com"`), not a derived form.
- No conflict with the existing design docs identified for this slice.

## 7. Open Questions / Decisions Pending *(if any; else "None")*

- **`created_by` value**: the v5 schema makes `created_by` NOT NULL DEFAULT `'system'`, so null is not an option. Open only whether to keep the `'system'` default or use a config-driven placeholder until auth exists. (Current draft: rely on the `'system'` default.)
- **Duplicate UX**: active duplicates `(entity_id, value, match_type)` are already DB-guarded by `uq_exclusions_entity_active_value_match` (`WHERE is_active = true AND entity_id IS NOT NULL`); the open part is only how the service/UI surfaces a rejected duplicate — not whether to allow it.
- **Mutation surface**: should this slice include Edit and/or Deactivate (`is_active` toggle), or stay strictly create + view? (Current draft: create + view only — see §2.)
- **Global exclusions**: support `entity_id = null` (global allowlist) now, or defer? (Current draft: deferred.)
- **`match_type = "pattern"`**: when `value` is a glob/regex rather than a domain, is any registration-time validation expected, or is it stored verbatim? (Application/matching is downstream regardless.)
