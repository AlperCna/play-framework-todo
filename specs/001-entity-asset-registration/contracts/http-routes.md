# Contract: HTTP Routes & Forms (server-rendered)

This slice exposes a small server-rendered (Twirl) admin surface. No JSON API. The contract below is the externally observable behavior the implementation MUST satisfy; internal controller/service design is left to implementation. All routes are unauthenticated in this slice (see spec Constraints).

Base path: `/drp/assets` (admin surface for the asset module). Exact path prefix is an implementation choice but MUST be consistent across the three routes.

---

## R1 — Listing screen

```
GET /drp/assets
```

- **Response**: `200 OK`, HTML page listing **all entities** and, under each, its registered assets (value, type, active status).
- **Empty-entity rule**: an entity with no assets renders with an empty asset list, not an error (FR-009, SC-003).
- **Forms**: the page hosts the entity-registration form and, per entity, an asset-registration form (or an equivalent flow that targets R2/R3).

## R2 — Register entity

```
POST /drp/assets/entities
```

- **Form fields**: `name` (required), `type` (required, free text).
- **Success**: entity persisted; respond `303 See Other` → `GET /drp/assets`; the new entity appears in the listing (FR-001, SC-001).
- **Validation failure** (blank `name`): re-render (or redirect back to) the listing with a **visible** validation message; **nothing persisted** (FR-002, SC-002).
- **Notes**: `type` other than "brand" (e.g., "institution") is accepted and shown as entered (FR-003, SC-004). Duplicate `name` is accepted (Clarification 2026-06-26).

## R3 — Register asset under an entity

```
POST /drp/assets/entities/:entityId/assets
```

- **Path param**: `entityId` (the owning entity).
- **Form fields**: `value` (required); optional reference metadata fields — `homepageUrl`, `loginPageUrl`, `logoRef`, `faviconRef`, `referenceDomSummary` (all optional, references only).
- **Success**: asset persisted with `asset_type = "domain"`, `is_active = true`, `value` stored exactly as entered; respond `303 See Other` → `GET /drp/assets`; the asset appears under its entity (FR-004, FR-007, SC-001, SC-005).
- **Validation / rejection** — each re-renders with a visible message and persists nothing:
  - blank `value` (FR-005, SC-002);
  - `:entityId` does not reference an existing entity (FR-005, SC-002);
  - active duplicate `(entityId, "domain", value)` already exists (FR-008, SC-006).
- **Notes**: a non-domain-looking but non-blank `value` is accepted and stored as-is (Clarification 2026-06-26). Binary/base64 content in metadata is not accepted (FR-006).

---

## Cross-cutting contract notes

- **Status codes**: successful mutations use POST→redirect→GET (`303`) so refreshes don't double-submit.
- **Persistence invariant**: every rejection path persists **zero** rows (validated before insert; DB constraints are the backstop, not the primary UX).
- **No JSON/REST surface** is introduced in this slice.
