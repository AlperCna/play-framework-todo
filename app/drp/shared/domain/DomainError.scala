package drp.shared.domain

/**
 * Closed set of DRP domain errors. Each value carries a stable `code` (i18n message key) and a
 * default human message. Smart constructors and services return `Either[DomainError, _]`.
 */
sealed trait DomainError {
  def code: String
  def message: String
}

object DomainError {

  case object EmptyEntityName extends DomainError {
    val code = "error.drp.entity.emptyName"
    val message = "Entity name cannot be empty."
  }
  case object EmptyAssetValue extends DomainError {
    val code = "error.drp.asset.emptyValue"
    val message = "Asset value cannot be empty."
  }
  case object EmptyAssetGroupName extends DomainError {
    val code = "error.drp.assetGroup.emptyName"
    val message = "Asset group name cannot be empty."
  }
  case object EmptyExclusionValue extends DomainError {
    val code = "error.drp.exclusion.emptyValue"
    val message = "Exclusion value cannot be empty."
  }

  final case class EntityNotFound(id: Long) extends DomainError {
    val code = "error.drp.entity.notFound"
    val message = s"Entity $id was not found."
  }
  final case class AssetGroupNotFound(id: Long) extends DomainError {
    val code = "error.drp.assetGroup.notFound"
    val message = s"Asset group $id was not found."
  }

  final case class DuplicateEntityName(name: String) extends DomainError {
    val code = "error.drp.entity.duplicateName"
    val message = s"An entity named '$name' already exists."
  }
  final case class DuplicateAsset(entityId: Long, assetType: String, value: String) extends DomainError {
    val code = "error.drp.asset.duplicate"
    val message = s"Asset '$value' ($assetType) already exists for this entity."
  }
  final case class DuplicateExclusion(entityId: Long, value: String, matchType: String) extends DomainError {
    val code = "error.drp.exclusion.duplicate"
    val message = s"Exclusion '$value' ($matchType) already exists for this entity."
  }

  final case class AssetGroupEntityMismatch(groupEntityId: Long, assetEntityId: Long) extends DomainError {
    val code = "error.drp.assetGroup.entityMismatch"
    val message = "An asset can only join an asset group that belongs to the same entity."
  }
}
