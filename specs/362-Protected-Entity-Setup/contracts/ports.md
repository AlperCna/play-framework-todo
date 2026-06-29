# Contracts — Ports & Read Seam (362)

Scala trait sketches (signatures, not bodies). All async = `Future[...]`; fallible domain ops = `Either[DomainError, _]` via `ServiceResult`. These live in `app/drp/asset/application/` (ports + services) and `app/drp/shared/application/` (ServiceResult, Page). Constitution I: writes through the owner; cross-module reads later via the read port returning **read-models**.

## Repository ports (`asset/application/ports/`)

```scala
trait EntityRepository {
  def add(e: Entity): Future[Entity]                          // returns persisted (id assigned)
  def get(id: EntityId): Future[Option[Entity]]
  def existsById(id: EntityId): Future[Boolean]
  def existsByName(name: String): Future[Boolean]            // duplicate pre-check (UX)
  def update(e: Entity): Future[Option[Entity]]
  def list(page: PageRequest): Future[Page[Entity]]          // top-level list → PAGINATED (FR-017)
}

trait AssetGroupRepository {
  def add(g: AssetGroup): Future[AssetGroup]
  def get(id: AssetGroupId): Future[Option[AssetGroup]]
  def update(g: AssetGroup): Future[Option[AssetGroup]]
  def listByEntity(entityId: EntityId): Future[Seq[AssetGroup]]   // one-parent bounded → full load
}

trait AssetRepository {
  def add(a: Asset): Future[Asset]
  def get(id: AssetId): Future[Option[Asset]]
  def existsActive(entityId: EntityId, assetType: AssetType, value: String): Future[Boolean]  // duplicate pre-check
  def update(a: Asset): Future[Option[Asset]]
  def listByEntity(entityId: EntityId): Future[Seq[Asset]]   // one-parent bounded → full load
}

trait ExclusionRepository {
  def add(x: Exclusion): Future[Exclusion]
  def get(id: ExclusionId): Future[Option[Exclusion]]
  def existsActive(entityId: EntityId, value: String, matchType: MatchType): Future[Boolean]
  def update(x: Exclusion): Future[Option[Exclusion]]
  def listActiveByEntity(entityId: EntityId): Future[Seq[Exclusion]]
}
```

Every port ships **two** implementations: a Slick adapter (`infrastructure/slick/`) on the `drp` datasource and an in-memory adapter (`infrastructure/inmemory/`) for tests.

## Services (`asset/application/`)

```scala
trait EntityService {
  def create(name: String, entityType: String): Future[Either[DomainError, Entity]]   // validates + duplicate-name
  def update(id: EntityId, name: String, entityType: String): Future[Either[DomainError, Entity]]
  def get(id: EntityId): Future[Option[Entity]]
  def list(page: PageRequest): Future[Page[Entity]]
}

trait AssetService {
  def create(entityId: EntityId, assetType: String, value: String,
             groupId: Option[AssetGroupId], metadata: AssetMetadata): Future[Either[DomainError, Asset]]
  // checks: parent entity exists (else EntityNotFound); group (if any) same entity (else AssetGroupEntityMismatch);
  //         duplicate (entity, asset_type, value) active (else DuplicateAsset)
  def update(id: AssetId, ...): Future[Either[DomainError, Asset]]
  def listByEntity(entityId: EntityId): Future[Seq[Asset]]
}

trait AssetGroupService {
  def create(entityId: EntityId, name: String): Future[Either[DomainError, AssetGroup]]  // parent must exist
  def update(id: AssetGroupId, name: String): Future[Either[DomainError, AssetGroup]]
  def listByEntity(entityId: EntityId): Future[Seq[AssetGroup]]
}

trait ExclusionService {
  def create(entityId: EntityId, value: String, matchType: String, reason: String): Future[Either[DomainError, Exclusion]]
  // entity-scoped (entityId required); created_by="system"; stored verbatim, never evaluated
  def update(id: ExclusionId, ...): Future[Either[DomainError, Exclusion]]
  def listActiveByEntity(entityId: EntityId): Future[Seq[Exclusion]]
}
```

## Read seam (`asset/application/ports/AssetReadPort`) — defined here, consumed later

```scala
final case class ExclusionView(value: String, matchType: String, reason: String)   // read-model
final case class EntityWithAssets(entity: EntityView, assets: Seq[AssetView])       // read-model

trait AssetReadPort {
  /** Active exclusions for an entity — the allowlist the future discovery module consults. No matching applied. */
  def activeExclusions(entityId: EntityId): Future[Seq[ExclusionView]]
  /** Resolve an entity together with its assets, for downstream anchoring. */
  def resolveEntityWithAssets(entityId: EntityId): Future[Option[EntityWithAssets]]
}
```

Returns typed **read-models**, never `domain` entities or Slick rows (Constitution I). Consumption (by discovery) is out of scope for this feature.
