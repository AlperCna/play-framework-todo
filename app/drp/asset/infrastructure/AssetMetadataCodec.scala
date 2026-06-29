package drp.asset.infrastructure

import play.api.libs.json.{Json, JsValue, OFormat}

import drp.asset.domain.AssetMetadata

/** JSONB <-> `AssetMetadata` (play-json). References-only; unreadable/absent JSON falls back to empty. */
object AssetMetadataCodec {
  implicit val format: OFormat[AssetMetadata] = Json.format[AssetMetadata]

  def toJson(m: AssetMetadata): JsValue = Json.toJson(m)

  def fromJson(js: JsValue): AssetMetadata = js.asOpt[AssetMetadata].getOrElse(AssetMetadata.empty)
}
