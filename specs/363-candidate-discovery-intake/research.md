# Research: Candidate Discovery Intake & Staging

**Feature**: 363-candidate-discovery-intake | **Date**: 2026-06-29

## Decision 1 — PSL-Aware Registrable-Domain Matching

**Decision**: Use Guava `com.google.common.net.InternetDomainName` for Public Suffix List (PSL) aware registrable-domain resolution.

**Rationale**: Guava is already a transitive dependency of Play Framework 2.9 — confirmed on the classpath with no new `build.sbt` entry required. `InternetDomainName.isUnderPublicSuffix()` and `.topPrivateDomain()` give a correct, maintained PSL-aware registrable-domain extraction. This avoids adding a dedicated PSL library and keeps the dependency footprint flat.

**Alternatives Considered**:
- `net.publicsuffix:psl4j` — lightweight and accurate, but a new dependency with its own update cadence. Unnecessary since Guava covers the need.
- Naive last-two-label extraction (`host.split('.').takeRight(2)`) — explicitly forbidden by the spec (US-363 §6). Fails on `.co.uk`, `.com.tr`, `.gov.au` and similar multi-part TLDs.

**How to apply**: `ExclusionMatcher` in `drp.discovery.application` imports `com.google.common.net.InternetDomainName`. Call `InternetDomainName.from(normalizedHostname).topPrivateDomain().toString` to obtain the registrable domain for `registrable_domain` match-type comparison. Wrap in `Try` to handle invalid hostnames gracefully (already excluded by `NormalizedValue` smart constructor, so this is a safety net only).

---

## Decision 2 — Hostname Normalisation (stdlib only, domain-safe)

**Decision**: Implement `NormalizedValue` smart constructor using `java.net.URI` and `java.net.IDN` (JDK stdlib) for all normalisation steps. No external library.

**Rationale**: All required operations (URL→hostname extraction, IDN→Punycode, lowercase, trailing-dot removal, www preservation) are achievable with JDK stdlib. Keeping the domain pure (no Play/Slick/JSON/external library) satisfies Constitution I.

**Algorithm (in order)**:
1. `value.trim` — strip surrounding whitespace.
2. Attempt `new URI(value)` — if the result has a non-null host, extract `uri.getHost.toLowerCase`. If `getHost` is null (bare hostname, no scheme), try `new URI("https://" + value.trim).getHost.toLowerCase` as a fallback.
3. `java.net.IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED)` — converts Unicode/IDN labels to Punycode. Already lowercase from step 2.
4. Remove trailing `.` if present.
5. Preserve `www` label as-is (no stripping).
6. Result: the normalized hostname string. If any step throws (malformed value that cannot be parsed), the value is declared malformed.

**Malformed fallback**: `"invalid:" + value.trim.toLowerCase.replaceAll("\\s+", " ")` — deterministic, unique per input, stored for traceability.

**Alternatives Considered**:
- Apache Commons Validator — would work but adds a dependency. Unnecessary.
- Custom regex parsing — fragile; URI/IDN is the correct abstraction.

---

## Decision 3 — Glob Pattern Matching for Exclusions

**Decision**: Implement a small pure Scala glob matcher (`GlobMatcher`) for the `pattern` exclusion match type. The pattern syntax is: `*` matches zero or more characters; `?` matches exactly one character. No regex interpretation.

**Rationale**: The spec explicitly says the pattern is a full-hostname glob and is NOT interpreted as a regular expression. A purpose-built O(n·m) match (or Scala regex converted from glob) is safer than exposing raw regex to user-supplied patterns. The implementation is small (~15 lines) and has no external dependency.

**Algorithm**: Convert the glob pattern to a `java.util.regex.Pattern` by escaping all regex metacharacters except `*` (→ `.*`) and `?` (→ `.`). `matches` the normalised discovery hostname against the anchored pattern.

**Alternatives Considered**:
- `org.apache.commons.io.FilenameUtils.wildcardMatch` — would work but adds a dependency. Overkill for a 15-line function.
- Raw `String.matches(pattern)` — would expose regex semantics; forbidden by spec.

---

## Decision 4 — Permutation Provider Interface

**Decision**: Define `PermutationProvider` as a trait in `drp.discovery.application.ports` with a single method: `generateLookAlikes(assetValue: String): Future[Seq[String]]`. The `FakePermutationProvider` is the only implementation required by this story; it returns a hardcoded deterministic list.

**Rationale**: The spec and US-363 mandate a replaceable provider boundary — the intake behavior must not embed a production algorithm. A single-method trait is the minimal abstraction. `Future[Seq[String]]` is the right return type because the future production dnstwist provider will call an external process/API asynchronously.

**Fake provider contract**: Constructor accepts `Seq[String]` as the fixed batch. Default seed used in tests: a small list containing at least one valid domain, one that duplicates an existing record, one that matches an active exclusion, and one malformed value — covering all intake paths deterministically.

**Provider failure modeling**: `Future.failed(PermutationProviderException(...))` — the service catches this and returns `Left(DomainError.PermutationProviderFailure(...))` without writing any staging records.

**Alternatives Considered**:
- `Either[ProviderError, Seq[String]]` return type — ruled out; the future dnstwist adapter will be async (process execution), so `Future` is the correct effect.
- Encoding algorithm parameters in the trait — rejected; algorithms belong to the implementing class, not the interface.

---

## Decision 5 — Asset Module Read-Seam Extension (`isActive`)

**Decision**: Add `isActive: Boolean` to `AssetView` in `drp.asset.application.ports.AssetReadPort`. Update `AssetReadPortImpl.resolveEntityWithAssets` to map `a.isActive`. No other asset file changes.

**Rationale**: `AssetView` is defined as "DEFINED here; consumption is out of scope" (its Scaladoc). There are currently **zero consumers** of `resolveEntityWithAssets` or `AssetView` outside the asset module — confirmed by codebase grep. The change is therefore non-breaking. Placing `isActive` on the read-model (not the domain entity) is the correct pattern: the discovery module must not import asset domain types.

**Impact**: Two file edits in the asset module, both additive. No test changes needed (no existing tests reference `AssetView` field positions; in-memory asset repositories already populate `isActive` from the `Asset` domain entity).

---

## Decision 6 — No New Migration

**Decision**: No new SQL migration file for this feature. `candidate_discoveries` already exists in `V002__discovery_layer_up.sql` with all required columns, constraints, unique index, and trigger.

**Rationale**: Schema is complete. The unique index `uq_candidate_discoveries_entity_normalized` on `(entity_id, normalized_value)` already enforces the deduplication invariant at the DB level. The `CHECK` constraints already cover `dns_status` and `skip_reason` values. No schema gap identified.

---

## Decision 7 — Bulk-Insert Strategy for Permutation Batches

**Decision**: For a permutation batch, the service fetches the set of existing `normalized_value`s for the entity in one query, performs in-memory deduplication against the provider results, then inserts only the unique new records — one `INSERT ... ON CONFLICT DO NOTHING` per unique value (or a batch insert). No per-value SELECT+INSERT cycle.

**Rationale**: Constitution IV forbids DB calls inside loops. A bulk fetch of existing normalized values (bounded by entity — small set in practice) followed by in-memory deduplication is the correct pattern. The `ON CONFLICT DO NOTHING` on the unique index acts as a race-safe last-mile guard.

**Alternatives Considered**:
- Per-value `findByEntityAndNormalized` inside the loop — forbidden by Constitution IV.
- Single bulk INSERT with `ON CONFLICT DO NOTHING` and no pre-fetch — works but gives no feedback on which values were skipped. Pre-fetch allows the service to report the count of staged vs. skipped values.
