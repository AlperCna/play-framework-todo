package drp.discovery.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.data.Form
import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents, MessagesRequestHeader, Result}

import drp.asset.application.ports.AssetReadPort
import drp.asset.domain.EntityId
import drp.discovery.application.{DiscoveryIntakeService, DiscoveryStatusFilter}
import drp.discovery.domain.DiscoveryId
import drp.shared.application.PageRequest

/** Thin web layer for manual discovery intake, list, and detail views. */
@Singleton
class DiscoveryController @Inject() (
    cc: MessagesControllerComponents,
    service: DiscoveryIntakeService,
    assetReadPort: AssetReadPort
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def list(entityId: Option[Long], status: Option[String], page: Int) = Action.async { implicit request =>
    entityId match {
      case None =>
        assetReadPort.listEntities(PageRequest.of(1, 200)).map { entitiesPage =>
          entitiesPage.items.headOption match {
            case Some(e) => Redirect(routes.DiscoveryController.list(Some(e.id), None, 1))
            case None    => Redirect(drp.asset.web.routes.EntityController.list(1))
          }
        }
      case Some(eid) =>
        val statusFilter = status.flatMap(DiscoveryStatusFilter.fromQueryParam)
        service.listDiscoveries(eid, statusFilter, PageRequest.of(page)).flatMap { discoveries =>
          for {
            entityOpt    <- assetReadPort.resolveEntityWithAssets(EntityId(eid))
            entitiesPage <- assetReadPort.listEntities(PageRequest.of(1, 200))
          } yield entityOpt match {
            case None => NotFound(request.messages("drp.entity.notFound"))
            case Some(ewa) =>
              val vms = discoveries.map { d =>
                val assetValue = d.assetId.flatMap(id => ewa.assets.find(_.id == id).map(_.value))
                DiscoveryViewModel.from(d, ewa.entity.name, assetValue)
              }
              val entityOptions = entitiesPage.items.map(e => e.id -> s"${e.name} (#${e.id})")
              Ok(drp.discovery.web.views.html.discoveryList(
                ewa.entity.name, eid, entityOptions, vms, statusFilter, page
              ))
          }
        }
    }
  }

  def newForm(entityId: Option[Long]) = Action.async { implicit request =>
    assetReadPort.listEntities(PageRequest.of(1, 200)).flatMap { entitiesPage =>
      val preselectedEntityId = entityId.orElse(entitiesPage.items.headOption.map(_.id))
      preselectedEntityId match {
        case None =>
          Future.successful(
            Ok(drp.discovery.web.views.html.discoveryForm(
              DiscoveryFormData.form,
              entitiesPage.items.map(e => e.id -> s"${e.name} (#${e.id})"),
              Seq.empty,
              entityId
            ))
          )
        case Some(eid) =>
          assetReadPort.resolveEntityWithAssets(EntityId(eid)).map { entityOpt =>
            val assetOptions = entityOpt.map(_.assets.map(a => a.id -> a.value)).getOrElse(Seq.empty)
            Ok(drp.discovery.web.views.html.discoveryForm(
              DiscoveryFormData.form.fill(DiscoveryFormData(eid, None, "")),
              entitiesPage.items.map(e => e.id -> s"${e.name} (#${e.id})"),
              assetOptions,
              entityId
            ))
          }
      }
    }
  }

  def submit = Action.async { implicit request =>
    DiscoveryFormData.form.bindFromRequest().fold(
      formWithErrors => renderBadRequest(
        formWithErrors,
        formWithErrors("entityId").value.flatMap(_.toLongOption)
      ),
      data =>
        service.submitManual(data.entityId, data.assetId, data.value).value.flatMap {
          case Right(d) =>
            Future.successful(
              Redirect(routes.DiscoveryController.list(Some(d.entityId), None, 1))
                .flashing("success" -> request.messages("drp.discovery.submitted"))
            )
          case Left(err) =>
            renderBadRequest(
              DiscoveryFormData.form.fill(data).withGlobalError(err.code),
              Some(data.entityId)
            )
        }
    )
  }

  def detail(id: Long) = Action.async { implicit request =>
    service.getDiscovery(DiscoveryId(id)).value.flatMap {
      case Left(_) => Future.successful(NotFound(request.messages("drp.discovery.notFound")))
      case Right(d) =>
        assetReadPort.resolveEntityWithAssets(EntityId(d.entityId)).map { entityOpt =>
          val entityName = entityOpt.map(_.entity.name).getOrElse("-")
          val assetValue = for {
            ewa <- entityOpt
            aid <- d.assetId
            asset <- ewa.assets.find(_.id == aid)
          } yield asset.value
          val vm = DiscoveryViewModel.from(d, entityName, assetValue)
          Ok(drp.discovery.web.views.html.discoveryDetail(vm))
        }
    }
  }

  private def renderBadRequest(
      form: Form[DiscoveryFormData],
      entityId: Option[Long]
  )(implicit request: MessagesRequestHeader): Future[Result] =
    assetReadPort.listEntities(PageRequest.of(1, 200)).flatMap { entitiesPage =>
      val entityOptions = entitiesPage.items.map(e => e.id -> s"${e.name} (#${e.id})")
      entityId match {
        case Some(eid) =>
          assetReadPort.resolveEntityWithAssets(EntityId(eid)).map { entityOpt =>
            val assetOptions = entityOpt.toSeq.flatMap(_.assets).map(a => a.id -> a.value)
            BadRequest(drp.discovery.web.views.html.discoveryForm(
              form, entityOptions, assetOptions, Some(eid)
            ))
          }
        case None =>
          Future.successful(BadRequest(drp.discovery.web.views.html.discoveryForm(
            form, entityOptions, Seq.empty, None
          )))
      }
    }
}
