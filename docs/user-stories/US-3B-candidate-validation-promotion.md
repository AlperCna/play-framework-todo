# User Story: Candidate Validation & Promotion

**US ID**: 3B  
**Type**: Feature Implementation  
**Status**: Draft  
**Summary (one sentence)**: The system asynchronously validates pending candidate discoveries through DNS and HTTP checks and promotes only reachable, non-excluded discoveries into `candidates` with `status = "validated"`.

---

## 1. Problem / Motivation (WHY)

- US-3A creates a controlled staging area, but pending discoveries are not yet verified or available to the downstream pipeline.
- DNS/HTTP validation may be slow, unreliable and retryable; it must not block the analyst's request.
- Inactive and temporarily unreachable domains must remain traceable and eligible for rechecking.
- Validation messages may be delivered more than once, so promotion must be idempotent.
- This story turns staged discoveries into real candidates and establishes the asynchronous processing pattern later pipeline workers will reuse.
- Tie-breaking guidance: stop after producing a `validated` candidate. Crawling and later lifecycle transitions remain outside this story.

## 2. Scope

- **In scope**:
  - Consuming pending, non-skipped discoveries created by US-3A.
  - The `platform/queue` abstraction required for candidate validation.
  - PGMQ-backed `candidate_validation_queue`.
  - Asynchronous discovery validation.
  - DNS resolution.
  - Basic HTTP reachability validation.
  - Updating:
    - `dns_status`;
    - `http_status_code`;
    - `failed_check_count`;
    - `last_checked_at`;
    - `next_check_at`;
    - `updated_at`.
  - Rechecking inactive/error discoveries according to the agreed retry policy.
  - The `candidate` module, owning `candidates`.
  - Promoting active discoveries through the candidate module's public application boundary.
  - Creating candidates with required `discovery_id` and `status = "validated"`.
  - Preserving candidate/discovery provenance.
  - Idempotent validation and promotion.
  - A defined handoff/read seam for the future crawler story.
  - Leaving promoted candidates queryable in `status = "validated"` for the future crawler story.
  - Displaying validation and promotion results in the existing discovery/candidate views.
  - Local end-to-end verification through PostgreSQL and PGMQ.

- **Out of scope** (MUST NOT be touched):
  - Manual intake behavior already delivered by US-3A.
  - Domain normalization or exclusion semantics changes.
  - Domain permutation generation.
  - CT Log, WHOIS or external discovery producers.
  - Actual crawling or browser execution.
  - Enqueueing or consuming crawl work.
  - Atomic crawl handoff.
  - HTTP response-body inspection.
  - HTML, DOM, screenshot, favicon, OCR or binary collection.
  - `crawl_results`, `blob_storage`, `page_features`, `candidate_asset_matches`, `detection_signals`, `risk_scores`, `rule_results`, `reviews`, `cases` and `evidence_files`.
  - Candidate lifecycle transitions after `validated`.
  - Dead-letter monitoring UI.
  - Authentication, RBAC or multi-tenant isolation.
  - Hard delete, soft delete or deactivation flows.

## 3. Type-Specific Core — Feature Implementation

- **Expected behavior**:
  - A pending, non-skipped discovery is submitted to `candidate_validation_queue`.
  - The queued message contains only the discovery reference, job type and small parameters.
  - The validation process retrieves the existing discovery and ignores records already marked `whitelisted` or `invalid_format`.
  - DNS validation is performed first.
  - A DNS failure:
    - records `dns_status = "inactive"`;
    - increments `failed_check_count`;
    - records `last_checked_at`;
    - sets `next_check_at` according to the retry policy;
    - creates no candidate.
  - A technical validation failure:
    - records `dns_status = "error"`;
    - increments `failed_check_count`;
    - records retry information;
    - creates no candidate.
  - If DNS resolves, basic HTTP reachability is checked.
  - A discovery satisfying the agreed DNS/HTTP rule:
    - records `dns_status = "active"`;
    - records `http_status_code`;
    - clears `next_check_at`;
    - is promoted through the candidate module.
  - Promotion creates a candidate with:
    - the same `entity_id`;
    - required `discovery_id`;
    - the discovery source;
    - value and normalized value;
    - `status = "validated"`;
    - small provenance-only metadata;
    - the original discovery time.
  - The candidate does not contain `asset_id`.
  - Promotion does not write `candidate_id` or `promoted_at` back to the discovery.
  - Reprocessing the same discovery does not create another candidate.
  - The resulting candidate remains in `validated` until a future crawler story consumes it.
  - This story does not enqueue crawl work; it only leaves promoted candidates queryable in `status = "validated"`.

- **Beyond the happy path**:
  - Queue message references an unknown discovery → message fails safely; no candidate.
  - Whitelisted or invalid discovery is received → ignored/completed without external validation.
  - Discovery is already active and promoted → repeated delivery completes without creating another candidate.
  - DNS resolves but HTTP does not satisfy the reachability rule → no candidate; retry state is recorded.
  - DNS/HTTP provider throws a transient error → discovery becomes `error` and remains retryable.
  - Retry later succeeds → existing discovery becomes active and is promoted once.
  - Retry limit is reached → no further automatic retry is scheduled; no candidate.
  - Concurrent promotion attempts → database invariants allow at most one active candidate.
  - Queue processing stops unexpectedly → message may be redelivered without duplicating business state.

- **To preserve**:
  - US-3A intake and staging behavior remains unchanged.
  - The discovery module remains the single writer for `candidate_discoveries`.
  - The candidate module remains the single writer for `candidates`.
  - Discovery code does not directly insert into `candidates`.
  - Every candidate has a non-null `discovery_id`.
  - `whitelisted` is not added to candidate statuses.
  - Candidate status starts at `validated` and does not progress further.
  - Queue messages remain small and reference-only.
  - No crawler, storage, analysis or decision table is written.

## 4. Acceptance Criteria / Done

- [ ] A pending discovery can be submitted to `candidate_validation_queue`.
- [ ] Candidate validation runs outside the analyst's HTTP request.
- [ ] A whitelisted or invalid discovery causes no DNS/HTTP request and no candidate.
- [ ] A DNS failure records `inactive`, increments `failed_check_count`, records check/recheck times and creates no candidate.
- [ ] A transient validation error records `error`, increments `failed_check_count` and creates no candidate.
- [ ] A DNS-resolved discovery proceeds to HTTP reachability validation.
- [ ] A discovery satisfying the agreed DNS/HTTP rule becomes `active` and records its HTTP status code.
- [ ] An active discovery creates exactly one candidate with `status = "validated"`.
- [ ] The candidate's `discovery_id` points to the source discovery and is never null.
- [ ] The candidate does not contain or require `asset_id`.
- [ ] Reprocessing the same queue message does not create a second candidate.
- [ ] Concurrent promotion attempts do not create duplicate active candidates.
- [ ] An inactive/error discovery may be retried according to the agreed policy.
- [ ] A successful retry updates the existing discovery rather than creating a new one.
- [ ] Reaching the retry limit prevents further automatic scheduling.
- [ ] Discovery and candidate views display the final validation/promotion result.
- [ ] The PGMQ-backed validation flow works end-to-end in the local Docker environment.
- [ ] The future crawler can identify candidates waiting in `validated` state through the defined seam.
- [ ] No crawl, storage, analysis, score, review, case or evidence row is created.
- [ ] Existing US-3A and Protected Entity Setup behavior continues to pass.

---

## 5. Assumptions & Preconditions

- US-3A Candidate Discovery Intake & Staging is complete.
- Pending discoveries, normalization and exclusion decisions are authoritative.
- `candidate_discoveries` and `candidates` follow the v5 schema.
- The PGMQ PostgreSQL extension and `candidate_validation_queue` are available through the local Docker Compose environment.
- The `JobQueue` Scala interface and `PgmqJobQueue` implementation do not yet exist; building them is in scope for this story.
- `candidates.discovery_id` is required.
- `candidates.asset_id` does not exist by design.
- The validation queue may deliver the same message more than once.

## 6. Constraints / Externally Imposed Decisions

- **Technology:** Scala 2.13, Play Framework, Slick/PostgreSQL, PGMQ and modular-monolith architecture.
- Validation MUST run through the project's `JobQueue` abstraction.
- Business logic MUST NOT call PGMQ directly.
- The specific worker runtime remains replaceable behind the application/queue boundary.
- The discovery module owns discovery state.
- The candidate module owns candidate creation and lifecycle.
- Cross-module promotion MUST use the candidate module's public application boundary.
- Validation and promotion MUST be idempotent.
- Queue messages contain only `target_type`, `target_id`, `job_type` and small parameters.
- Large content MUST NOT be placed in queue messages or candidate metadata.
- PostgreSQL ENUM MUST NOT be introduced.
- Foreign keys remain `ON DELETE RESTRICT`.
- A promoted candidate starts in `validated`.
- `whitelisted` MUST NOT be added to `candidates.status`.
- No Kafka is introduced for the MVP.

## 7. Open Questions / Decisions Pending

- Which DNS result is sufficient to classify a discovery as resolved?
- Which HTTP outcomes count as reachable: any valid response, only 2xx, or 2xx plus redirects?
- What is the retry limit for inactive/error discoveries?
- What backoff schedule determines `next_check_at`?
- When is a discovery considered permanently abandoned?
- Which runtime consumes PGMQ messages: a Future-based consumer, Akka Typed or Play scheduler?
