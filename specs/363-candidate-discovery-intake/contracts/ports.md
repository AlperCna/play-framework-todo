# Contracts: Repository Ports & Provider Interface

**Feature**: 363-candidate-discovery-intake | **Date**: 2026-06-29

---

## 1. `DiscoveryRepository` (new port — `drp.discovery.application.ports`)

The sole persistence interface for `candidate_discoveries`. Implemented by `SlickDiscoveryRepository` (production) and `InMemoryDiscoveryRepository` (tests).

```scala
package drp.discovery.application.ports

import scala.concurrent.Future

import drp.discovery.application.DiscoveryStatusFilter
import drp.discovery.domain.{CandidateDiscovery, DiscoveryId, NormalizedValue}
import drp.shared.application.{Page, PageRequest}

/** Persistence boundary for candidate_discoveries. Single-writer: DiscoveryIntakeService is the only caller. */
trait DiscoveryRepository {

  /** Persist a new discovery (id = 0 on input; returns the saved record with DB-assigned id). */
  def save(discovery: CandidateDiscovery): Future[CandidateDiscovery]

  /** Look up a single discovery by id. */
  def get(id: DiscoveryId): Future[Option[CandidateDiscovery]]

  /**
   * Find an existing discovery by entity + normalized value.
   * Used for the duplicate guard before attempting a save.
   */
  def findByEntityAndNormalized(entityId: Long, normalizedValue: NormalizedValue): Future[Option[CandidateDiscovery]]

  /**
   * Bulk-fetch all normalized values for an entity (used to deduplicate a permutation batch in-memory).
   * Returns only the normalized-value strings — not full records — to keep the payload small.
   */
  def listNormalizedValuesByEntity(entityId: Long): Future[Set[String]]

  /**
   * Paginated list for a given entity, with optional status filter.
   * None = no filter (show all). Ordered reverse-chronologically (created_at DESC).
   */
  def listByEntity(
    entityId: Long,
    statusFilter: Option[DiscoveryStatusFilter],
    page: PageRequest
  ): Future[Page[CandidateDiscovery]]
}
```

---

## 2. `PermutationProvider` (new port — `drp.discovery.application.ports`)

The replaceable boundary for requesting look-alike domain values. Decouples intake behavior from any specific algorithm or external tool.

```scala
package drp.discovery.application.ports

import scala.concurrent.Future

/**
 * Replaceable boundary for requesting look-alike domain variants.
 * The discovery module depends on this trait only — never on a concrete algorithm.
 * MVP implementation: FakePermutationProvider (deterministic, no external process).
 * Future implementation: DnstwistPermutationProvider (external process / API call).
 */
trait PermutationProvider {

  /**
   * Returns a batch of look-alike domain strings for the given asset hostname.
   * Each string is a raw, un-normalised candidate value.
   * Fails the Future on provider error — the caller MUST NOT write any partial batch.
   */
  def generateLookAlikes(assetValue: String): Future[Seq[String]]
}
```

**`FakePermutationProvider`** (infrastructure, deterministic):
- Constructor: `FakePermutationProvider(seed: Seq[String])`.
- Default test seed covers: at least one valid domain, one that duplicates a pre-existing record, one that matches an active exclusion, one malformed value, and one empty string (to verify blank-filtering).
- Used by `DiscoveryModule` when `drp.inMemory = true` or `Mode.Test`.

---

## 3. `AssetReadPort` (existing — additive change only)

`AssetView` gains `isActive: Boolean`. No method signature changes.

```scala
// drp.asset.application.ports.AssetReadPort (AFTER)

final case class AssetView(id: Long, assetType: String, value: String, isActive: Boolean)  // ← isActive added

// All other read-models and method signatures unchanged:
// final case class ExclusionView(value: String, matchType: String, reason: String)
// final case class EntityView(id: Long, name: String, entityType: String)
// final case class EntityWithAssets(entity: EntityView, assets: Seq[AssetView])
// trait AssetReadPort {
//   def activeExclusions(entityId: EntityId): Future[Seq[ExclusionView]]
//   def resolveEntityWithAssets(entityId: EntityId): Future[Option[EntityWithAssets]]
// }
```

---

## 4. `DiscoveryIntakeService` (application service — `drp.discovery.application`)

```scala
package drp.discovery.application

import scala.concurrent.Future

import drp.discovery.domain.CandidateDiscovery
import drp.shared.application.{Page, PageRequest, ServiceResult}
import drp.shared.domain.DomainError

/** Orchestrates manual intake and permutation intake through the same normalise → dedupe → exclusion-check → save pipeline. */
trait DiscoveryIntakeService {

  /**
   * Submit a suspicious domain manually.
   * Validates entity exists, validates asset belongs to entity (if provided),
   * normalises, exclusion-checks, deduplicates, and saves.
   */
  def submitManual(
    entityId: Long,
    assetId: Option[Long],
    rawValue: String
  ): ServiceResult[CandidateDiscovery]

  /**
   * Request permutation intake for an active domain asset.
   * Validates asset is active and domain-type, calls provider, stages all unique results.
   * Returns the count of newly staged discoveries.
   * Fails entirely on provider error — no partial batch written.
   */
  def requestPermutation(assetId: Long): ServiceResult[Int]

  /** Paginated list with optional status filter. */
  def listDiscoveries(
    entityId: Long,
    statusFilter: Option[DiscoveryStatusFilter],
    page: PageRequest
  ): Future[Page[CandidateDiscovery]]

  /** Single record lookup. */
  def getDiscovery(id: Long): ServiceResult[CandidateDiscovery]
}
```

---

## 5. In-Memory Test Adapter Contract

`InMemoryDiscoveryRepository` must faithfully implement all `DiscoveryRepository` methods using a mutable `Map[DiscoveryId, CandidateDiscovery]` and enforce the `(entityId, normalizedValue)` uniqueness invariant in memory (return the existing record from `findByEntityAndNormalized`; skip duplicates in `listNormalizedValuesByEntity`).
