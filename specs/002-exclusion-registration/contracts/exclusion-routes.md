# Contract: HTTP Routes & Forms (server-rendered)

This slice adds a small, **per-entity** server-rendered (Twirl) admin surface for exclusions. No JSON
API. The contract below is the externally observable behavior the implementation MUST satisfy; internal
controller/service design is left to implementation. All routes are unauthenticated in this slice (see
spec Constraints).

Base path: `/drp/assets/entities/:entityId/exclusions` (the exclusion surface is nested under its owning
entity). The existing US-001 surface (`GET /drp/assets`, `POST /drp/assets/entities`) is **unchanged**;
no navigation link is added to the `/drp/assets` listing view in this slice (see plan D5).

---

## R1 — Per-entity exclusions listing screen

```
GET /drp/assets/entities/:entityId/exclusions
```

- **Path param**: `entityId` (the owning entity).
- **Response (entity exists)**: `200 OK`, HTML page headed `Exclusions for entity #<entityId>`, hosting
  the exclusion-registration form (R2) and listing that entity's exclusions
  (value, match type, reason, active status, timestamps).
- **Empty rule**: an entity with no exclusions renders with an empty list, not an error (FR-008, SC-003).
- **Entity missing**: respond `303 See Other` → `GET /drp/assets` with a visible error message
  (the entity must exist; guarded via `existsById`).

## R2 — Register an exclusion under an entity

```
POST /drp/assets/entities/:entityId/exclusions
```

- **Path param**: `entityId` (the owning entity).
- **Form fields**:
  - `value` (required) — the exclusion target, stored exactly as entered.
  - `matchType` (required) — one of `exact` / `registrable_domain` / `subdomain_of` / `pattern`
    (rendered as a select; defaults to `exact`).
  - `reason` (required) — open text (e.g. `owned_unmonitored`, `third_party_legit`), stored as entered.
- **Success**: exclusion persisted with `is_active = true`, `created_by = 'system'` (DB default),
  `value` stored exactly as entered; respond `303 See Other` → `GET .../exclusions`; the new exclusion
  appears in the listing (FR-001, FR-005, FR-007, SC-001, SC-004, SC-005).
- **Validation / rejection** — each redirects back to R1 with a **visible** message and persists
  **nothing**:
  - blank `value` (FR-002, SC-002);
  - blank `reason` (FR-002, SC-002);
  - `matchType` outside the four allowed values (FR-004, SC-002);
  - `:entityId` does not reference an existing entity (FR-003, SC-002);
  - active duplicate `(entityId, value, matchType)` already exists (FR-006, SC-006).
- **Notes**: a non-domain-looking but non-blank `value` is accepted and stored as-is; `matchType =
  "pattern"` stores a glob/regex `value` verbatim with no registration-time validation (spec edge cases).

---

## Cross-cutting contract notes

- **Status codes**: successful mutations use POST→redirect→GET (`303`) so refreshes don't double-submit.
- **Persistence invariant**: every rejection path persists **zero** rows (validated before insert; the DB
  CHECK / FK / partial unique index are the backstop, not the primary UX).
- **No JSON/REST surface** is introduced in this slice.
- **US-001 untouched**: the existing `/drp/assets` routes, controllers, and `list.scala.html` are not
  modified.
