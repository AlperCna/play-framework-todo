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

  final case class InvalidAssetType(raw: String) extends DomainError {
    val code = "error.drp.asset.invalidType"
    val message = s"'$raw' is not a valid asset type (expected: domain or subdomain)."
  }
  final case class AssetNotFound(id: Long) extends DomainError {
    val code = "error.drp.asset.notFound"
    val message = s"Asset $id was not found."
  }
  final case class InvalidMatchType(raw: String) extends DomainError {
    val code = "error.drp.exclusion.invalidMatchType"
    val message = s"'$raw' is not a valid match type (exact, registrable_domain, subdomain_of, pattern)."
  }
  final case class ExclusionNotFound(id: Long) extends DomainError {
    val code = "error.drp.exclusion.notFound"
    val message = s"Exclusion $id was not found."
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
  final case class DuplicateAssetGroupName(entityId: Long, name: String) extends DomainError {
    val code = "error.drp.assetGroup.duplicateName"
    val message = s"An asset group named '$name' already exists for this entity."
  }

  case object EmptyDiscoveryValue extends DomainError {
    val code = "error.drp.discovery.emptyValue"
    val message = "Discovery value cannot be empty."
  }
  final case class AssetEntityMismatch(assetId: Long, entityId: Long) extends DomainError {
    val code = "error.drp.discovery.assetEntityMismatch"
    val message = s"Asset $assetId does not belong to entity $entityId."
  }
  final case class AssetNotDomainType(assetId: Long) extends DomainError {
    val code = "error.drp.discovery.assetNotDomainType"
    val message = s"Asset $assetId is not a domain-type asset."
  }
  final case class AssetNotActive(assetId: Long) extends DomainError {
    val code = "error.drp.discovery.assetNotActive"
    val message = s"Asset $assetId is not active."
  }
  final case class DiscoveryNotFound(id: Long) extends DomainError {
    val code = "error.drp.discovery.notFound"
    val message = s"Discovery $id was not found."
  }
  final case class PermutationProviderFailure(message: String) extends DomainError {
    val code = "error.drp.discovery.permutationProviderFailure"
  }
}
