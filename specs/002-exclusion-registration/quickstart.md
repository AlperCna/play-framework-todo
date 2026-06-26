# Quickstart & Validation: Exclusion (Allowlist) Registration

A runnable guide proving the slice works end to end (form → PostgreSQL → server-rendered page).
Validates the spec's acceptance criteria. This is a validation/run guide — implementation lives in
`tasks.md` / the implement phase.

## Prerequisites

- Docker Desktop running.
- `.env` present (copy from `.env.example` if missing).
- The asset-layer schema applied to the local PostgreSQL — **a manual step** (the app never
  auto-migrates). `V001` created `exclusions` alongside `entities`/`assets`:

```powershell
# One-time: start Postgres+PGMQ and apply V001..V006
.\scripts\setup.ps1
# (or, if the container is already up:)  .\scripts\migrate_drp_up.ps1
```

Expected: migrations report `V001__asset_layer_up.sql ... OK` and `exclusions` exists.

## Run

```powershell
sbt run
```

1. Open `http://localhost:9000/drp/assets` and register an entity (e.g. name "Akbank", type "brand").
   Note its **id** from the listing.
2. Open the per-entity exclusions screen by URL:
   `http://localhost:9000/drp/assets/entities/<id>/exclusions`.

> No link to the exclusions screen is added to the `/drp/assets` list in this slice (the US-001 view
> stays untouched); navigate by the entity-scoped URL above.

## Validation scenarios (map 1:1 to acceptance criteria)

Let `<id>` be an existing entity's id and `<missing>` an id with no entity.

| # | Action | Expected | Criterion |
|---|---|---|---|
| 1 | Under entity `<id>`, register value "akbankdirekt.com", match type "exact", reason "owned_unmonitored" | Exclusion appears in the listing: value exactly "akbankdirekt.com", match type `exact`, reason `owned_unmonitored`, active, `created_by = system`, timestamps set | FR-001, SC-001 |
| 2 | Submit an exclusion with blank `value` | Visible validation message; nothing persisted | FR-002, SC-002 |
| 3 | Submit an exclusion with blank `reason` | Visible validation message; nothing persisted | FR-002, SC-002 |
| 4 | POST an exclusion under entity `<missing>` | Rejected; nothing persisted | FR-003, SC-002 |
| 5 | Register with match type "registrable_domain" (and separately "subdomain_of", "pattern") | Accepted and stored with that match type | FR-004 |
| 6 | Submit a match type outside the four (e.g. "wildcard") | Rejected with a visible message; nothing persisted | FR-004, SC-002 |
| 7 | Register with reason "third_party_legit" | Accepted; reason stored exactly as entered | FR-005, SC-004 |
| 8 | Register "akbankdirekt.com" / "exact" under `<id>` a second time (still active) | Rejected with a visible message; no duplicate row | FR-006, SC-006 |
| 9 | Register value "not a domain" (non-blank, no dot) | Accepted, stored as-is | FR-007, SC-005 / edge case |
| 10 | Open the exclusions screen for an entity with no exclusions | Empty list, no error | FR-008, SC-003 |

## Automated checks

```powershell
sbt test
```

Expected: new `drp.asset` exclusion ScalaTest specs pass (domain validation in `ExclusionSpec`; services
via in-memory repositories in `ExclusionServiceSpec`) **and** all pre-existing suites (todo + US-001
entity specs) still pass (Constitution: tests stay green).

## Done / sign-off

- [ ] Scenarios 1–10 pass by manual observation on the screen.
- [ ] `sbt test` green (new + existing).
- [ ] No rows persisted on any rejection path (spot-check `exclusions` after scenarios 2, 3, 4, 6, 8).
- [ ] US-001 untouched: `/drp/assets` listing, entity/asset registration, and `list.scala.html` behave
      exactly as before; `slick.dbs.default` (todo / SQL Server) untouched; DRP uses `slick.dbs.drp`.
