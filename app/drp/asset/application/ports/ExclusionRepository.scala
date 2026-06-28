package drp.asset.application.ports

import scala.concurrent.Future

import drp.asset.domain.{Exclusion, ExclusionMatchType}

/**
 * Port for persisting and reading exclusions. The asset module is the single writer of the
 * `exclusions` table. Owning-entity existence is validated through `EntityRepository.existsById`.
 */
trait ExclusionRepository {

  /** Persist a new exclusion; returns it with the database-assigned id. */
  def create(exclusion: Exclusion): Future[Exclusion]

  /** All exclusions for one entity, ordered by id (bulk read — no per-row query). */
  def listByEntity(entityId: Long): Future[Seq[Exclusion]]

  /** Whether an active exclusion already exists for this (entityId, value, matchType). */
  def existsActiveDuplicate(entityId: Long, value: String, matchType: ExclusionMatchType): Future[Boolean]
}
