# Quickstart: Candidate Discovery Intake & Staging

**Feature**: 363-candidate-discovery-intake | **Date**: 2026-06-29

This guide validates the feature works end-to-end in a local environment. No migration is needed — `candidate_discoveries` already exists from V002.

---

## Prerequisites

1. Local PostgreSQL (Docker) is running: `docker compose up -d` (see `docs/drp-local-setup.md`).
2. Migrations V001–V007 are applied: `scripts/migrate_drp_up.ps1` (or `.sh`).
3. The application is started: `sbt run` (or equivalent).
4. At least one entity and one active domain asset exist. If not, create them via:
   - `GET /drp/entities/new` → create entity (e.g. "Akbank")
   - `GET /drp/assets/new` → create domain asset (e.g. `akbank.com`, type `domain`)
   - `GET /drp/exclusions/new` → create one active exclusion (e.g. value `akbank.com`, match type `exact`) for the entity

---

## Scenario 1: Manual Intake — Valid Domain

1. Open `GET /drp/discoveries/new?entityId=<entityId>`.
2. Confirm the entity selector pre-selects the entity.
3. Enter `https://akbank-guvenli-giris.com/login?ref=1` in the value field.
4. Submit.
5. **Expected**: Redirect to the discovery list or detail page. The record shows:
   - `value` = `https://akbank-guvenli-giris.com/login?ref=1`
   - `normalized_value` = `akbank-guvenli-giris.com`
   - `source` = `manual`
   - `dns_status` = `pending`
   - `skip_reason` = (empty — eligible)

---

## Scenario 2: Normalisation Rules

Submit each value and confirm the expected `normalized_value`:

| Input | Expected `normalized_value` |
|---|---|
| `AKBANK-GUVENLI.COM.` | `akbank-guvenli.com` (lowercase, trailing dot removed) |
| `https://www.akbank-fake.com/path?q=1` | `www.akbank-fake.com` (www preserved, path/query discarded) |
| `xn--nxasmq6b.com` | `xn--nxasmq6b.com` (already Punycode, unchanged) |
| `http://user:pass@akbank-fake.com:8080/x` | `akbank-fake.com` (credentials, port, path discarded) |

---

## Scenario 3: Exclusion Match → Whitelisted

1. Ensure an active `exact` exclusion exists for `akbank.com` under the entity.
2. Submit `akbank.com` manually.
3. **Expected**: Record stored with `skip_reason = whitelisted`; status label shows "Whitelisted" in the list.

Test all four exclusion match types:

| Exclusion value | Match type | Submitted discovery | Expected outcome |
|---|---|---|---|
| `akbank.com` | `exact` | `akbank.com` | whitelisted |
| `akbank.com` | `registrable_domain` | `www.akbank.com` | whitelisted (same registrable domain) |
| `akbank.com` | `subdomain_of` | `login.akbank.com` | whitelisted; `akbank.com` itself → NOT whitelisted by this rule |
| `*akbank*` | `pattern` | `secure-akbank-login.com` | whitelisted |

---

## Scenario 4: Malformed Input → Invalid Format

1. Submit `not a domain !! @@` (non-empty, malformed).
2. **Expected**: Record stored with:
   - `skip_reason = invalid_format`
   - `normalized_value` = `invalid:not a domain !! @@` (trimmed + lowercased + whitespace collapsed)
3. Submit the same value again.
4. **Expected**: No second record — deduplicated by `(entity_id, normalized_value)`.

---

## Scenario 5: Duplicate Detection

1. Submit `akbank-guvenli-giris.com` (valid, no exclusion match).
2. Confirm record with `dns_status = pending`.
3. Submit `AKBANK-GUVENLI-GIRIS.COM.` (same value, different case and trailing dot).
4. **Expected**: No second record created. The existing record is unchanged.

---

## Scenario 6: Cross-Entity Asset Rejection

1. Create a second entity (e.g. "Garanti").
2. Create a domain asset under "Garanti".
3. Open the manual intake form for "Akbank", select the "Garanti" asset in the asset dropdown.
4. Submit.
5. **Expected**: Error shown; no record created.

---

## Scenario 7: Permutation Intake

1. Navigate to `GET /drp/assets/<id>` where the asset is an active domain asset.
2. Confirm the "Request permutation intake" link is present.
3. Click the link → `GET /drp/permutation-intake/new?assetId=<id>`.
4. Confirm the form shows the asset's domain value.
5. Submit the confirmation form.
6. **Expected**: Redirect to the discovery list for the entity. New records are visible with `source = permutation`.
7. Trigger permutation intake again for the same asset.
8. **Expected**: No duplicate records. Already-staged values are silently skipped.

---

## Scenario 8: Status Filter

1. Ensure the discovery list contains at least one `pending`, one `whitelisted`, and one `invalid_format` record.
2. Open `GET /drp/discoveries?entityId=<id>&status=pending` — confirm only pending records are shown.
3. Open `GET /drp/discoveries?entityId=<id>&status=whitelisted` — confirm only whitelisted records.
4. Open `GET /drp/discoveries?entityId=<id>&status=invalid_format` — confirm only invalid records.
5. Open `GET /drp/discoveries?entityId=<id>` (no filter) — confirm all records shown.

---

## Automated Tests (ScalaTest)

Run with `sbt test`:

| Suite | What it covers |
|---|---|
| `NormalizedValueSpec` | All normalisation rules, malformed fallback, idempotency |
| `ExclusionMatcherSpec` | All four match types; non-matching cases (zero false positives) |
| `DiscoveryIntakeServiceSpec` | Happy path, exclusion match, malformed, duplicate, cross-entity asset, permutation batch, provider failure, empty provider result |
| `FakePermutationProviderSpec` | Deterministic seed behaviour |

All existing tests must remain green: `sbt test` must exit 0.
