# User Story: Protected Entity & Asset Registration (Foundational Vertical Slice)

**US ID**: [DevOps work item no.]
**Type**: Feature Implementation
**Status**: Draft
**Summary (one sentence)**: An analyst can register a protected entity (brand / institution / person) and one or more domain assets under it, persisted in PostgreSQL and viewable on a server-rendered screen — establishing the entity→asset foundation the whole detection pipeline anchors to.

---

## 1. Problem / Motivation (WHY) *(mandatory)*

- Every record in Mona DRP anchors to a protected entity and its assets: `entity_id` is the owner anchor everywhere, and `candidate_discoveries`, `candidates`, `candidate_asset_matches`, etc. all reference back to `entities` / `assets`. In the current greenfield state there is no way to declare *what we are protecting*, so no downstream step (candidate generation, crawl, similarity, scoring, review) can exist or be demoed.
- The concrete pain: today an analyst cannot tell the system "protect **Akbank** / `akbank.com`". Until this exists, the product has no foundation and nothing is queryable.
- Once solved, the system has a real, persisted foundation that every later US builds on, **and** the team gets the first uncut proof that the chosen stack works end to end (HTTP request → persistence → server-rendered page) inside the modular monolith. The agent should treat "establish a clean, demoable foundation" as the tie-breaker for any ambiguity below.

## 2. Scope *(mandatory)*

- **In scope**:
  - Entity registration capability: create an entity with `name` and `type`, and store it.
  - Asset registration capability: under an existing entity, register one or more domain assets (`asset_type = "domain"`, `value`, optional reference fields in `metadata`, `is_active` defaulting to true), and store them.
  - A server-rendered (Twirl) screen that lists entities and, per entity, their registered assets.
  - Wiring the app to the **already-written** asset-layer schema (`app/migrations/drp-postgres/V001__asset_layer_up.sql`) so the existing `entities` / `assets` tables are used — this US authors **no** new migration and **runs none** (migrations are applied manually as a human/setup step).
  - The minimal module wiring needed to host these two capabilities inside the modular monolith.
- **Out of scope** (strictly off-limits — MUST NOT be built or touched):
  - `exclusions` and `asset_groups` management (`asset_group_id` stays nullable and unused).
  - `candidate_discoveries`, `candidates`, and everything downstream of them (DNS/HTTP validation, crawler, feature extraction, similarity, detection signals, risk scoring, review, cases, evidence).
  - `blob_storage` and any binary / logo / favicon **upload or storage** — `metadata` holds *references only*, no binary content.
  - PGMQ / JobQueue, workers, and any asynchronous pipeline behavior.
  - Multi-tenant isolation, authentication / RBAC, and population of any `created_by`-style fields.
  - Edit / delete / deactivation of entities or assets (this slice is **create + view** only) — see §7.
  - Any use of DB-level `enum` types.

## 3. Type-Specific Core *(mandatory — Feature Implementation block)*

**▸ Type ∈ {Feature Implementation, Feature Enhancement, Bug Fixing}**
- **Expected behavior**:
  - An analyst creates an entity by providing `name` and `type` (e.g., `"Akbank"` / `"brand"`); the row is stored with audit stamps.
  - Under an existing entity, the analyst registers one or more domain assets by providing the domain `value` (e.g., `"akbank.com"`), with `asset_type = "domain"` and, optionally, a small set of reference fields inside `metadata` (`homepage_url`, `login_page_url`, `logo_ref`, `favicon_ref`, `reference_dom_summary`). New assets are stored with `is_active = true`.
  - A server-rendered page lists all entities and, for each, its registered assets.
- **Beyond the happy path** (known edge / boundary cases the slice must handle gracefully — i.e., visible, non-crashing):
  - Blank `name`, or blank asset `value` → rejected with a visible validation message; nothing is persisted.
  - Registering an asset under a non-existent entity → rejected; nothing is persisted.
  - An entity with zero assets → valid; shown with an empty asset list.
  - `type` set to a recognized-but-non-`brand` value (e.g., `"institution"`, `"person"`) → accepted (string field, not a fixed enum).
- **To preserve**:
  - Greenfield — there is no prior runtime behavior to preserve. The two invariants that MUST hold: (1) conformance to the v5 data-model column contract (`docs/migration_final_schema/migration_final_schema.md`), and (2) the modular-monolith boundary — entity/asset logic must not leak into unrelated modules.
- *(Bug Fixing only)*: N/A

## 4. Acceptance Criteria / Done *(mandatory)*

- [ ] On a fresh DB, manually applying the already-written asset-layer migration (`V001__asset_layer_up.sql`, run via `scripts/migrate_drp_up.*` / `setup` — the app never auto-migrates) creates the asset-layer tables (`entities`, `assets`, plus `asset_groups` and `exclusions`) with the v5 columns and succeeds with no hand-editing of the SQL.
- [ ] Creating entity `"Akbank"` (type `"brand"`) persists a row with `id`, `name`, `type`, `created_at`, `updated_at`, and it appears on the listing screen.
- [ ] Registering domain asset `"akbank.com"` under that entity persists an `assets` row with the correct `entity_id`, `asset_type = "domain"`, `value = "akbank.com"`, `is_active = true`, and audit stamps; it appears under that entity on screen.
- [ ] Registering an asset whose `metadata` contains only reference fields (`homepage_url`, `login_page_url`, `logo_ref`, `favicon_ref`, `reference_dom_summary`) is accepted and stored.
- [ ] Submitting an empty entity `name` shows a validation error and persists nothing.
- [ ] Submitting an asset under a non-existent entity is rejected and persists nothing.
- [ ] `type` and `asset_type` are stored as strings (no DB enum); an entity with `type = "institution"` is accepted and stored as-is.
- [ ] An entity with no assets renders correctly (empty asset list, not an error).

---

## 5. Assumptions & Preconditions *(if any; else "None")*

- A PostgreSQL instance is reachable, and the Play application boots in the current greenfield skeleton.
- The modular-monolith directory structure exists (currently empty) and can host new module code.
- The table-creation SQL for `entities` and `assets` is already written and available to apply.
- No authentication layer exists yet; the screen is reachable without login in this slice.
- The **v5 data-model** — `docs/migration_final_schema/migration_final_schema.md` (and `docs/research_documents/Mona_DRP_Veri_Modeli_v5_duzeltilmis.pdf`) — is the authoritative source for column names, types, nullability, defaults, and constraints.

## 6. Constraints / Externally Imposed Decisions *(if any; else "None")*

- Backend is **Scala Play Framework**; the UI is **server-rendered Twirl** (no React). *(architecture decision)*
- Database is **PostgreSQL**; architecture is a **modular monolith**. *(architecture decision)*
- `type` and `asset_type` are stored as **DB strings**, represented in code via sealed/open enum + a single codec — **not** DB-level enums. *(data-model design rule)*
- Audit convention: mutable tables (`entities`, `assets`) carry `created_at` + `updated_at`.
- `assets.metadata` is JSONB holding **references / summaries only** (`homepage_url`, `login_page_url`, `logo_ref`, `favicon_ref`, `reference_dom_summary`); binary/base64 content MUST NOT be embedded (that path is `blob_storage`, out of scope here).
- `asset_group_id` is nullable and intentionally left unused in this US.
- `value` stores the asset as-is (e.g., `"akbank.com"`), not a derived/registrable form.
- **Migrations**: schema is managed as manual, versioned SQL under `app/migrations/drp-postgres/` — **not** Play Evolutions / Flyway, and the app **never** auto-migrates on startup. The asset-layer file (`V001__asset_layer_up.sql`) is already written; this US writes no migration and executes none (applying it is a manual human/setup step). *(settled project decision — CLAUDE.md §11, constitution "Migration discipline")*
- No conflict with the existing design docs identified for this slice.

## 7. Open Questions / Decisions Pending *(if any; else "None")*

- **Duplicate policy for `entities.name`**: should the same entity name be unique/blocked, warned, or freely allowed? (At the DB level the v5 schema places **no** unique constraint on `entities.name`.) Note: active **asset** duplicates are already DB-guarded by `uq_assets_active_entity_type_value = UNIQUE(entity_id, asset_type, value) WHERE is_active = true`, so the open part is only how the service/UI should *surface* a rejected duplicate asset — not whether to allow it.
- **Mutation surface**: should this first slice include Edit and/or Deactivate (`is_active` toggle), or stay strictly create + view?
- **Exclusions bundling**: `exclusions` is part of the §0 setup in the happy path. Ship it inside this US, or as a separate follow-up US? (Current draft: separate follow-up.)
- **Asset entry flow**: register multiple assets per entity at once, or one-at-a-time and repeatable? (Current draft: one-at-a-time, repeatable.)
