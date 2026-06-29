package drp.discovery.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import drp.asset.application.ports.AssetReadPort
import drp.asset.domain.EntityId
import drp.discovery.application.DiscoveryIntakeService

/** Confirmation form for triggering permutation intake from an asset detail page. */
@Singleton
class PermutationIntakeController @Inject() (
    cc: MessagesControllerComponents,
    service: DiscoveryIntakeService,
    assetReadPort: AssetReadPort
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def newForm(assetId: Long) = Action.async { implicit request =>
    assetReadPort.resolveAsset(assetId).flatMap {
      case None => Future.successful(NotFound(request.messages("drp.asset.notFound")))
      case Some(asset) =>
        if (asset.assetType != "domain")
          Future.successful(
            Redirect(drp.asset.web.routes.EntityController.view(asset.entityId))
              .flashing("error" -> request.messages("error.drp.discovery.assetNotDomainType"))
          )
        else if (!asset.isActive)
          Future.successful(
            Redirect(drp.asset.web.routes.EntityController.view(asset.entityId))
              .flashing("error" -> request.messages("error.drp.discovery.assetNotActive"))
          )
        else
          assetReadPort.resolveEntityWithAssets(EntityId(asset.entityId)).map { entityOpt =>
            val entityName = entityOpt.map(_.entity.name).getOrElse("-")
            Ok(drp.discovery.web.views.html.permutationIntakeForm(
              PermutationIntakeFormData.form.fill(PermutationIntakeFormData(asset.entityId, assetId)),
              asset.value,
              entityName,
              assetId
            ))
          }
    }
  }

  def submit = Action.async { implicit request =>
    PermutationIntakeFormData.form.bindFromRequest().fold(
      formWithErrors =>
        formWithErrors("assetId").value.flatMap(_.toLongOption) match {
          case Some(assetId) =>
            Future.successful(
              Redirect(routes.PermutationIntakeController.newForm(assetId))
                .flashing("error" -> request.messages("error.required"))
            )
          case None =>
            Future.successful(
              Redirect(drp.asset.web.routes.EntityController.list())
                .flashing("error" -> request.messages("error.required"))
            )
        },
      data =>
        service.requestPermutation(data.entityId, data.assetId).value.map {
          case Right(count) =>
            Redirect(routes.DiscoveryController.list(Some(data.entityId), None, 1))
              .flashing("success" -> request.messages("drp.permutation.staged", count))
          case Left(err) =>
            Redirect(routes.PermutationIntakeController.newForm(data.assetId))
              .flashing("error" -> request.messages(err.code))
        }
    )
  }
}
