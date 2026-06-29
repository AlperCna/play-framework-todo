# User Story: Candidate Discovery Intake & Staging

**US ID**: 363
**Type**: Feature Implementation  
**Status**: Ready  
**Summary (one sentence)**: An analyst can submit a suspicious domain or request look-alike values through a replaceable provider for a protected entity, and the system normalizes, deduplicates and exclusion-checks each value before retaining it in `candidate_discoveries` for later validation.

---

## 1. Problem / Motivation (WHY)

- Protected Entity Setup defines the entities and digital assets the system protects, but there is no controlled intake point for suspicious domains.
- Raw discoveries must not be written directly to `candidates`.
- Manual and automated discovery sources need one staging boundary where entity ownership, normalization, duplicates, invalid formats and exclusions are handled consistently.
- Legitimate or allowlisted domains must be stopped before DNS/HTTP validation and downstream processing.
- This story establishes that intake boundary without introducing queue, worker or pipeline-execution complexity.
- Tie-breaking guidance: prefer a synchronous, server-rendered intake flow. DNS/HTTP validation, promotion, queue processing and crawling remain outside this story.

## 2. Scope

- **In scope**:
  - The `discovery` module, owning `candidate_discoveries`.
  - A Twirl form for manual suspicious domain/URL submission.
  - Selecting an existing protected entity.
  - Optionally associating the discovery with an asset belonging to that entity.
  - Creating manual discoveries with `source = "manual"`.
  - Requesting look-alike domain values from a replaceable permutation-provider boundary for an active domain asset.
  - Staging every value returned by the provider through the same intake path with `source = "permutation"`.
  - A deterministic fake permutation provider for local verification and automated tests.
  - Preserving the submitted value.
  - Producing and storing `normalized_value`.
  - Preventing duplicates through `(entity_id, normalized_value)`.
  - Reading entity, asset and active exclusion information through the asset module's public read seam.
  - Extending the asset module's read-only `AssetView` projection to expose `isActive`, without adding any asset write behavior.
  - Validating that the selected asset belongs to the selected entity.
  - Evaluating active entity-scoped exclusions.
  - Recording exclusion matches with `skip_reason = "whitelisted"`.
  - Recording non-empty malformed input with `skip_reason = "invalid_format"`.
  - Leaving valid, non-excluded discoveries eligible for validation with `dns_status = "pending"` and `skip_reason = NULL`.
  - A server-rendered discovery list/detail view.
  - A public application boundary through which future discovery producers can submit discoveries.

- **Out of scope** (MUST NOT be touched):
  - DNS or HTTP validation.
  - Candidate promotion or writing to `candidates`.
  - PGMQ, `JobQueue`, workers or background scheduling.
  - Automatic retry or recheck behavior.
  - Production dnstwist integration and process execution.
  - Permutation algorithms, variant-family selection, homoglyph/IDN impersonation, NLP-based generation or permutation quality tuning.
  - CT Log, WHOIS, complaint or external-feed integrations.
  - Crawl handoff or `crawl_queue`.
  - Crawling, HTML, DOM, screenshot, OCR or binary collection.
  - Similarity, scoring, review, case and evidence processing.
  - Changes to entity, asset, asset-group or exclusion records.
  - Global/entity-less exclusion management.
  - Authentication, RBAC or multi-tenant isolation.
  - Hard delete, soft delete or deactivation flows.

## 3. Type-Specific Core — Feature Implementation

- **Expected behavior**:
  - An analyst selects an existing protected entity and enters a suspicious domain or URL.
  - The analyst may optionally select an asset belonging to that entity.
  - The original value is retained for provenance.
  - A canonical representation is stored in `normalized_value`.
  - For a valid URL or hostname, canonicalization:
    - trims surrounding whitespace;
    - extracts the hostname when a URL is supplied;
    - converts an IDN hostname to its ASCII/Punycode form;
    - lowercases the hostname;
    - removes a trailing DNS dot;
    - ignores scheme, credentials, port, path, query and fragment;
    - preserves the `www` label instead of removing it.
  - A non-empty malformed value uses the deterministic fallback `invalid:` followed by its trimmed, lowercased value with consecutive whitespace collapsed to one space. This guarantees that retained invalid discoveries still have a stable `normalized_value`.
  - A valid, non-excluded discovery is stored with:
    - `source = "manual"`;
    - `dns_status = "pending"`;
    - `skip_reason = NULL`;
    - `failed_check_count = 0`;
    - no HTTP/check/recheck result.
  - Active exclusions are evaluated before the discovery becomes eligible for validation.
  - Exclusion values and discovery values use the same hostname canonicalization before matching.
  - Exclusion matching is defined as:
    - `exact`: the normalized discovery hostname equals the normalized exclusion hostname;
    - `registrable_domain`: both normalized hostnames have the same registrable domain;
    - `subdomain_of`: the discovery hostname is a strict subdomain of the exclusion hostname; the exclusion hostname itself does not match;
    - `pattern`: the exclusion value is a full-hostname glob in which `*` matches zero or more characters and `?` matches exactly one character. It is not interpreted as a regular expression.
  - An exclusion match is retained with `skip_reason = "whitelisted"` and is not eligible for validation.
  - A non-empty malformed value is retained with `skip_reason = "invalid_format"`.
  - The same normalized value under the same entity does not create a second discovery.
  - The same normalized value may be submitted independently under another entity.
  - Permutation intake applies only to an active asset with `asset_type = "domain"`.
  - The system requests a batch of look-alike values through a replaceable provider boundary. It does not depend on a particular permutation algorithm or tool.
  - The future production provider will use dnstwist without changing the discovery intake behavior defined by this story.
  - Each distinct provider result is staged independently; a duplicate result is ignored without failing the remaining values.
  - Validation eligibility is exactly `dns_status = "pending" AND skip_reason IS NULL`.
  - The discovery list clearly identifies pending, whitelisted and invalid discoveries.

- **Beyond the happy path**:
  - Blank input → inline validation error; no row is created.
  - Unknown entity → clear error; no row is created.
  - Asset belongs to another entity → clear error; no row is created.
  - No asset selected → discovery may be created with `asset_id = NULL`.
  - Duplicate normalized value → existing discovery remains authoritative; no second row.
  - Exclusion match → discovery is retained as whitelisted but never marked pending for external validation.
  - Non-empty malformed value → retained as invalid for traceability.
  - Same malformed value submitted again under the same entity → the `invalid:` prefix produces the same `normalized_value`; the `(entity_id, normalized_value)` unique guard fires and the existing invalid discovery remains authoritative — no second row.
  - One invalid submission must not change existing asset or exclusion data.
  - Permutation intake requested for an unknown, inactive or non-domain asset → clear error; no provider results are staged.
  - A provider value already staged under the entity → ignored without preventing other unique values from being staged.
  - The permutation provider returns an empty result → the request completes without creating discoveries.
  - The permutation provider fails → a clear failure is returned and no partial discovery batch is written.

- **To preserve**:
  - Protected Entity Setup behavior remains unchanged.
  - The asset module remains the single writer for `entities`, `assets`, `asset_groups` and `exclusions`.
  - Discovery consumes asset data only through the asset module's public read seam.
  - The discovery module is the single writer for `candidate_discoveries`.
  - No code in this story writes to `candidates`.
  - `candidate_discoveries` remains lightweight.
  - No downstream pipeline table is written.

## 4. Acceptance Criteria / Done

- [ ] An analyst can open a Twirl form and submit a suspicious domain under an existing entity.
- [ ] A valid submission creates a discovery with `source = "manual"`, `dns_status = "pending"` and `failed_check_count = 0`.
- [ ] The original value and normalized value are both visible.
- [ ] URL scheme, credentials, port, path, query and fragment do not affect the normalized hostname.
- [ ] Hostname case and a trailing DNS dot do not affect the normalized hostname.
- [ ] The `www` label remains part of the normalized hostname.
- [ ] The selected asset must belong to the selected entity.
- [ ] Asset activity is obtained through the asset module's public read seam; discovery does not query asset-owned tables directly.
- [ ] A discovery may be created without an asset, resulting in `asset_id = NULL`.
- [ ] Unknown entity or cross-entity asset input shows a clear error and creates no row.
- [ ] Blank input shows an inline error and creates no row.
- [ ] Non-empty malformed input creates a discovery with `skip_reason = "invalid_format"`.
- [ ] A retained malformed discovery receives the documented deterministic `invalid:` normalized-value fallback.
- [ ] A non-empty malformed value returned by the permutation provider follows the same intake behavior as malformed manual input and is retained with `skip_reason = "invalid_format"`.
- [ ] The same normalized value cannot create two discoveries under the same entity.
- [ ] The same normalized value may be submitted under another entity.
- [ ] Active exclusions are evaluated during intake.
- [ ] `exact`, `registrable_domain`, `subdomain_of` and glob-based `pattern` exclusions follow the matching rules defined by this story.
- [ ] An exclusion match creates a discovery with `skip_reason = "whitelisted"`.
- [ ] Whitelisted and invalid discoveries are not eligible for later validation.
- [ ] A discovery is validation-eligible only when `dns_status = "pending"` and `skip_reason IS NULL`.
- [ ] An analyst can request permutation intake for an active domain asset.
- [ ] Look-alike values are obtained through a replaceable provider boundary rather than an algorithm embedded in the discovery module.
- [ ] A deterministic fake provider can supply a known batch for local verification and automated tests.
- [ ] Every unique provider value is staged with `source = "permutation"` and the source asset id.
- [ ] Duplicate provider values are ignored without preventing other unique values from being staged.
- [ ] Permutation intake for an unknown, inactive or non-domain asset is rejected without writing discoveries.
- [ ] An empty provider result creates no discovery and is not treated as an error.
- [ ] A provider failure creates no partial discovery batch.
- [ ] Replacing the fake provider with a future dnstwist adapter requires no change to normalization, duplicate detection, exclusion matching or staging behavior.
- [ ] A list/detail view shows entity, optional asset, value, normalized value, source, DNS status and skip reason.
- [ ] No candidate or queue message is created.
- [ ] Existing Protected Entity Setup behavior and tests continue to pass.

---

## 5. Assumptions & Preconditions

- Protected Entity Setup is complete and stable.
- The asset module exposes active exclusions and entity/assets through a public read seam.
- `candidate_discoveries` follows the existing v5 schema.
- `candidate_discoveries.asset_id` remains nullable.
- At least one entity and active asset exist for manual verification.
- The production permutation provider will use dnstwist in a later story; this story proves only the provider-independent intake contract.

## 6. Constraints / Externally Imposed Decisions

- **Technology:** Scala 2.13, Play Framework, Twirl SSR, Slick/PostgreSQL and modular-monolith architecture.
- All raw inputs MUST enter through `candidate_discoveries`.
- The discovery module owns `candidate_discoveries`.
- Direct Slick access to asset-owned tables is forbidden.
- PostgreSQL ENUM MUST NOT be introduced; existing `TEXT + CHECK` conventions remain.
- Foreign keys remain `ON DELETE RESTRICT`.
- `whitelisted` is represented through discovery `skip_reason`.
- Large or crawler-derived content MUST NOT be stored in discovery records.
- The discovery module MUST depend on a replaceable permutation-provider boundary and MUST NOT embed a production permutation algorithm.
- Registrable-domain matching MUST use a public-suffix-aware domain parsing approach; naive last-two-label comparison is not acceptable.
- No React or client-side SPA is introduced.

## 7. Open Questions / Decisions Pending

- None.
