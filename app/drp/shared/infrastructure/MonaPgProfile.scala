package drp.shared.infrastructure

import com.github.tminglei.slickpg._

/**
 * Slick profile for the DRP PostgreSQL datasource: standard PostgreSQL (slick-pg `ExPostgresProfile`)
 * plus JSON(B) column support backed by play-json. Referenced by `slick.dbs.drp.profile` and by every
 * DRP Slick table that maps a JSONB column (e.g. `assets.metadata`).
 *
 * NOTE: the `api`/JSON wiring follows slick-pg's documented play-json profile shape; the exact member
 * names are slick-pg-version-sensitive — verify on first compile.
 */
trait MonaPgProfile extends ExPostgresProfile with PgPlayJsonSupport {

  // Use JSONB (not JSON) for all play-json columns.
  override def pgjson: String = "jsonb"

  override val api: MonaApi = new MonaApi {}

  trait MonaApi extends API with PlayJsonImplicits
}

object MonaPgProfile extends MonaPgProfile
