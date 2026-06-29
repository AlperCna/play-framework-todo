# Contracts — Web Routes (362)

Server-rendered Twirl screens under a `/drp/...` prefix, added as a commented group in the single `conf/routes`. **Create + edit + list/view only — no delete route anywhere** (FR-012). No auth gate this feature (spec Clarification Q1). CSRF: Play default filters on; every form includes `@helper.CSRF.formField`.

```text
# ── DRP : Protected Entity Setup (362) ─────────────────────────────
# Entities (top-level list is paginated)
GET   /drp/entities                      drp.asset.web.EntityController.list(page: Int ?= 1)
GET   /drp/entities/new                  drp.asset.web.EntityController.newForm
POST  /drp/entities                      drp.asset.web.EntityController.create
GET   /drp/entities/:id                  drp.asset.web.EntityController.view(id: Long)
GET   /drp/entities/:id/edit             drp.asset.web.EntityController.editForm(id: Long)
POST  /drp/entities/:id                  drp.asset.web.EntityController.update(id: Long)

# Asset groups (entity-scoped)
GET   /drp/entities/:entityId/asset-groups/new   drp.asset.web.AssetGroupController.newForm(entityId: Long)
POST  /drp/entities/:entityId/asset-groups       drp.asset.web.AssetGroupController.create(entityId: Long)
GET   /drp/asset-groups/:id/edit                 drp.asset.web.AssetGroupController.editForm(id: Long)
POST  /drp/asset-groups/:id                       drp.asset.web.AssetGroupController.update(id: Long)

# Assets (entity-scoped; list rendered within the entity view)
GET   /drp/entities/:entityId/assets/new   drp.asset.web.AssetController.newForm(entityId: Long)
POST  /drp/entities/:entityId/assets       drp.asset.web.AssetController.create(entityId: Long)
GET   /drp/assets/:id/edit                  drp.asset.web.AssetController.editForm(id: Long)
POST  /drp/assets/:id                        drp.asset.web.AssetController.update(id: Long)

# Exclusions (entity-scoped)
GET   /drp/entities/:entityId/exclusions/new   drp.asset.web.ExclusionController.newForm(entityId: Long)
POST  /drp/entities/:entityId/exclusions       drp.asset.web.ExclusionController.create(entityId: Long)
GET   /drp/exclusions/:id/edit                 drp.asset.web.ExclusionController.editForm(id: Long)
POST  /drp/exclusions/:id                       drp.asset.web.ExclusionController.update(id: Long)
```

**Controller contract** (mirrors the established web convention, CLAUDE.md §9.1):
- Extend `MessagesAbstractController`; i18n via `request.messages("key")`.
- `bindFromRequest().fold(formWithErrors => BadRequest(view(...)), data => service...)`.
- Service result mapping: `Right(_) → Redirect(...).flashing("success" -> msg)`; `Left(DomainError.*NotFound) → NotFound`; `Left(err) → BadRequest(view(form.withGlobalError(messages(err.code))))`.
- Views receive a **typed view-model** (+ `Form`/`Call`/primitives) only — no `domain`/Slick types (Constitution I).
- The entity **view** page composes: entity fields + its assets (full) + its asset-groups + its active exclusions (full).
