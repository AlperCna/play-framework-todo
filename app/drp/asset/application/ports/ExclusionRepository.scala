package drp.asset.application.ports

import scala.concurrent.Future

import drp.asset.domain.{EntityId, Exclusion, ExclusionId, MatchType}

/** Persistence port for exclusions (the `asset` module is the single writer of `exclusions`). */
trait ExclusionRepository {
  def add(x: Exclusion): Future[Exclusion]
  def get(id: ExclusionId): Future[Option[Exclusion]]
  /** True if an ACTIVE exclusion already has this (entity, value, match_type). */
  def existsActive(entityId: EntityId, value: String, matchType: MatchType): Future[Boolean]
  def update(x: Exclusion): Future[Option[Exclusion]]
  /** Active exclusions for one entity — the allowlist the future discovery module reads. */
  def listActiveByEntity(entityId: EntityId): Future[Seq[Exclusion]]
}
