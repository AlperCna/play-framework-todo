package drp.shared.infrastructure

import com.github.tminglei.slickpg.{ExPostgresProfile, PgPlayJsonSupport}

/**
 * Shared Slick profile for all DRP modules: PostgreSQL (slick-pg `ExPostgresProfile`) plus
 * JSONB support backed by Play-JSON. JSONB is used by the asset module's `metadata` column
 * (and later DRP modules); entity tables use only the standard column types.
 */
trait MonaPgProfile extends ExPostgresProfile with PgPlayJsonSupport {

  override val pgjson: String = "jsonb"

  override val api: MonaApi.type = MonaApi

  object MonaApi extends API with JsonImplicits
}

object MonaPgProfile extends MonaPgProfile
