package drp.asset.domain

/** Domain-level failures for the asset module (entity & asset registration). */
sealed trait AssetDomainError {
  def code: String
  def message: String
}

object AssetDomainError {

  case object BlankEntityName extends AssetDomainError {
    val code = "drp.asset.error.blankEntityName"
    val message = "Entity name cannot be blank."
  }

  case object BlankAssetValue extends AssetDomainError {
    val code = "drp.asset.error.blankAssetValue"
    val message = "Asset value cannot be blank."
  }

  final case class UnknownEntity(entityId: Long) extends AssetDomainError {
    val code = "drp.asset.error.unknownEntity"
    val message = s"No entity exists with id $entityId."
  }

  final case class DuplicateActiveAsset(entityId: Long, value: String) extends AssetDomainError {
    val code = "drp.asset.error.duplicateActiveAsset"
    val message = s"An active asset '$value' already exists under entity $entityId."
  }
}
