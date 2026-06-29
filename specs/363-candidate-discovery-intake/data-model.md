# Data Model: Candidate Discovery Intake & Staging

**Feature**: 363-candidate-discovery-intake | **Date**: 2026-06-29

---

## 1. Existing Table (No Migration Required)

`candidate_discoveries` exists in `V002__discovery_layer_up.sql`. All columns, constraints, unique index, and trigger are already in place. This document maps the DB schema to the Scala domain model.

```
candidate_discoveries
─────────────────────────────────────────────────────
id                BIGSERIAL PK
entity_id         BIGINT NOT NULL  → FK entities(id) ON DELETE RESTRICT
asset_id          BIGINT NULL      → FK assets(id) ON DELETE RESTRICT
value             TEXT NOT NULL    (original submitted value)
normalized_value  TEXT NOT NULL    (deterministic canonical form)
source            TEXT NOT NULL DEFAULT 'permutation'
                  CHECK IN ('manual', 'permutation')        ← enforced by app; CHECK not in V002 but added as a constraint
dns_status        TEXT NOT NULL DEFAULT 'pending'
                  CHECK IN ('pending', 'active', 'inactive', 'error')
http_status_code  INT NULL
skip_reason       TEXT NULL
                  CHECK IS NULL OR IN ('whitelisted', 'duplicate', 'invalid_format')
failed_check_count INT NOT NULL DEFAULT 0
last_checked_at   TIMESTAMPTZ NULL
next_check_at     TIMESTAMPTZ NULL
created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()

UNIQUE INDEX: uq_candidate_discoveries_entity_normalized (entity_id, normalized_value)
TRIGGER: trg_candidate_discoveries_set_updated_at
```

---

## 2. Domain Entities

### 2.1 `CandidateDiscovery` (final case class)

The core aggregate for the discovery module. Created only through its smart constructor; never modified after creation (immutable lifecycle — only the DNS/HTTP validation step in a later story will update it).

| Field | Type | Notes |
|---|---|---|
| `id` | `DiscoveryId` | 0 = unsaved; DB assigns `BIGSERIAL` |
| `entityId` | `Long` | References entity (not an imported domain type) |
| `assetId` | `Option[Long]` | Nullable; references asset |
| `value` | `String` | Original submitted value; preserved verbatim |
| `normalizedValue` | `NormalizedValue` | Canonical hostname (or `invalid:` prefixed fallback) |
| `source` | `DiscoverySource` | `Manual` or `Permutation` |
| `dnsStatus` | `DnsStatus` | Defaults to `Pending` on intake |
| `skipReason` | `Option[SkipReason]` | `None` = eligible; `Some(Whitelisted)` / `Some(InvalidFormat)` |
| `failedCheckCount` | `Int` | Always 0 on intake |
| `httpStatusCode` | `Option[Int]` | Always `None` on intake |
| `lastCheckedAt` | `Option[Instant]` | Always `None` on intake |
| `nextCheckAt` | `Option[Instant]` | Always `None` on intake |
| `createdAt` | `Instant` | DB-managed |
| `updatedAt` | `Instant` | DB-managed (trigger) |

**Smart constructor `CandidateDiscovery.intake`**:
- Accepts `(entityId, assetId, rawValue, source)`.
- Produces a `NormalizedValue` from `rawValue`.
- Returns `CandidateDiscovery` with `dnsStatus = Pending`, `skipReason = None`, `failedCheckCount = 0`, all check fields `None`.
- Does NOT apply exclusion logic (that happens in the service before calling the constructor).

**Validation-eligible predicate**: `skipReason.isEmpty && dnsStatus == DnsStatus.Pending`.

---

### 2.2 `NormalizedValue` (value object)

Encapsulates the canonical hostname string and the normalisation algorithm. Domain-safe: uses only `java.net.URI` and `java.net.IDN` (JDK stdlib — no external library).

| Aspect | Rule |
|---|---|
| Trim | Strip surrounding whitespace first |
| URL → hostname | Extract `URI.getHost`; if no host (bare hostname), prepend `https://` and retry |
| IDN → Punycode | `java.net.IDN.toASCII` with `ALLOW_UNASSIGNED` |
| Case | Lowercase |
| Trailing dot | Remove if present |
| `www` label | Preserved (not stripped) |
| Scheme, credentials, port, path, query, fragment | Discarded |
| Malformed (non-empty, cannot parse) | `"invalid:" + trimmed.toLowerCase.replaceAll("\\s+", " ")` |
| Blank | Not reachable via smart constructor — blank input rejected at form validation before reaching the domain |

`NormalizedValue(value: String)` — `value` is always the final canonical string. The companion `apply` is replaced by a factory method `NormalizedValue.from(raw: String): NormalizedValue` that applies all rules and never fails (malformed → `invalid:` prefix).

---

### 2.3 `DiscoverySource` (sealed trait — closed set)

```
sealed trait DiscoverySource { def code: String }
object DiscoverySource:
  Manual      → code = "manual"
  Permutation → code = "permutation"
```

DB column: `source TEXT NOT NULL`. `fromCode` returns `Option[DiscoverySource]`; unknown code = `None`.

---

### 2.4 `DnsStatus` (sealed trait — closed set, mutable in future stories)

```
sealed trait DnsStatus { def code: String }
object DnsStatus:
  Pending  → code = "pending"    ← intake default
  Active   → code = "active"
  Inactive → code = "inactive"
  Error    → code = "error"
```

DB column: `dns_status TEXT NOT NULL DEFAULT 'pending'` with CHECK constraint.

---

### 2.5 `SkipReason` (sealed trait — closed set for this story)

```
sealed trait SkipReason { def code: String }
object SkipReason:
  Whitelisted   → code = "whitelisted"    ← exclusion match
  InvalidFormat → code = "invalid_format" ← malformed input
```

DB column: `skip_reason TEXT NULL` with CHECK. `None` = eligible for validation. `"duplicate"` is defined in the DB CHECK but not used by this story — `fromCode` handles it defensively.

---

## 3. Value Types

| Type | Wraps | Notes |
|---|---|---|
| `DiscoveryId` | `Long` | `extends AnyVal`; 0 = unsaved |
| `NormalizedValue` | `String` | Smart constructor in companion; see §2.2 |

---

## 4. Filter / Query Type

### `DiscoveryStatusFilter` (sealed trait — used in list query)

```
sealed trait DiscoveryStatusFilter
object DiscoveryStatusFilter:
  PendingValidation → dns_status = 'pending' AND skip_reason IS NULL
  Whitelisted       → skip_reason = 'whitelisted'
  InvalidFormat     → skip_reason = 'invalid_format'
```

Passed as `Option[DiscoveryStatusFilter]` to `DiscoveryRepository.listByEntity`; `None` = no filter (show all).

---

## 5. New Domain Errors (additions to `drp.shared.domain.DomainError`)

| Error case | Code | When raised |
|---|---|---|
| `EmptyDiscoveryValue` | `error.drp.discovery.emptyValue` | Blank raw value (belt-and-suspenders; form validation is the primary guard) |
| `AssetEntityMismatch(assetId, entityId)` | `error.drp.discovery.assetEntityMismatch` | Asset does not belong to the selected entity |
| `AssetNotDomainType(assetId)` | `error.drp.discovery.assetNotDomainType` | Permutation requested for a non-domain asset |
| `AssetNotActive(assetId)` | `error.drp.discovery.assetNotActive` | Permutation requested for an inactive asset |
| `PermutationProviderFailure(message)` | `error.drp.discovery.permutationProviderFailure` | Provider call fails; no partial batch written |

Existing `DomainError.EntityNotFound(id)` and `DomainError.AssetNotFound(id)` are reused as-is.

---

## 6. Asset Module Read-Model Change

`AssetView` in `drp.asset.application.ports.AssetReadPort` gains one field:

```scala
// BEFORE
final case class AssetView(id: Long, assetType: String, value: String)

// AFTER
final case class AssetView(id: Long, assetType: String, value: String, isActive: Boolean)
```

`AssetReadPortImpl.resolveEntityWithAssets` maps `a.isActive` in the `assets.map(...)` call. No other asset file changes. No existing consumers of `AssetView` — confirmed by codebase grep.

---

## 7. Exclusion Matching Rules (implemented in `application`, not `domain`)

`ExclusionMatcher` in `drp.discovery.application`:

| Match type | Rule | Library used |
|---|---|---|
| `exact` | `normalizedDiscovery == normalizedExclusion` | None |
| `registrable_domain` | `topPrivateDomain(normalizedDiscovery) == topPrivateDomain(normalizedExclusion)` | Guava `InternetDomainName` |
| `subdomain_of` | `normalizedDiscovery.endsWith("." + normalizedExclusion)` — exact exclusion host does NOT match itself | None |
| `pattern` | Glob match: `*` → `.*`, `?` → `.` (converted to anchored regex) | JDK `java.util.regex` |

A discovery matches an exclusion if ANY active exclusion for the entity produces a positive match. First match wins; result is `SkipReason.Whitelisted`.

---

## 8. Key Invariants (enforced by DB + service)

1. `(entity_id, normalized_value)` unique — DB unique index `uq_candidate_discoveries_entity_normalized` is the authoritative guard; service deduplication is UX-level.
2. A discovery with `skip_reason IS NOT NULL` is NEVER eligible for DNS/HTTP validation.
3. `failed_check_count >= 0` — DB CHECK.
4. `asset_id` is only set when the selected asset is confirmed to belong to the selected entity.
5. No row is ever written to `candidates` by this module.
