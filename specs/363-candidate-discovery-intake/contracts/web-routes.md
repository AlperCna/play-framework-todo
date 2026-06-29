# Contracts: Web Routes

**Feature**: 363-candidate-discovery-intake | **Date**: 2026-06-29

All routes are added to `conf/routes` under a `# --- Discovery ---` comment block.

---

## Discovery Routes

| Method | Path | Controller | Action | Notes |
|---|---|---|---|---|
| `GET` | `/drp/discoveries` | `DiscoveryController` | `list(entityId: Long, status: Option[String] ?= None, page: Int ?= 1)` | Paginated list for a given entity; `status` = `pending` / `whitelisted` / `invalid_format` / empty (all) |
| `GET` | `/drp/discoveries/new` | `DiscoveryController` | `newForm(entityId: Option[Long] ?= None)` | Manual intake form; `entityId` pre-selects the entity |
| `POST` | `/drp/discoveries` | `DiscoveryController` | `submit` | Process manual intake form |
| `GET` | `/drp/discoveries/:id` | `DiscoveryController` | `detail(id: Long)` | Single-record detail view |

## Permutation Intake Routes

| Method | Path | Controller | Action | Notes |
|---|---|---|---|---|
| `GET` | `/drp/permutation-intake/new` | `PermutationIntakeController` | `newForm(assetId: Long)` | Confirmation form pre-filled with asset; linked from asset detail page |
| `POST` | `/drp/permutation-intake` | `PermutationIntakeController` | `submit` | Trigger permutation intake; redirects to discovery list on success |

---

## Asset Detail Page Addition

`app/drp/asset/web/AssetController.scala` (or its detail Twirl view) gains a link/button:

```
GET /drp/permutation-intake/new?assetId=<assetId>
```

This link is only rendered when `asset.assetType == "domain"` and `asset.isActive == true`. It routes to `PermutationIntakeController.newForm`.

---

## Route file additions (`conf/routes`)

```
# --- Discovery ---
GET    /drp/discoveries                    drp.discovery.web.DiscoveryController.list(entityId: Long, status: Option[String] ?= None, page: Int ?= 1)
GET    /drp/discoveries/new               drp.discovery.web.DiscoveryController.newForm(entityId: Option[Long] ?= None)
POST   /drp/discoveries                   drp.discovery.web.DiscoveryController.submit
GET    /drp/discoveries/:id               drp.discovery.web.DiscoveryController.detail(id: Long)

# --- Permutation Intake ---
GET    /drp/permutation-intake/new        drp.discovery.web.PermutationIntakeController.newForm(assetId: Long)
POST   /drp/permutation-intake            drp.discovery.web.PermutationIntakeController.submit
```

---

## View-Model Shapes

### `DiscoveryViewModel` (flat, Twirl-safe)

```scala
final case class DiscoveryViewModel(
  id:              Long,
  entityId:        Long,
  entityName:      String,
  assetId:         Option[Long],
  assetValue:      Option[String],
  value:           String,
  normalizedValue: String,
  source:          String,      // "manual" | "permutation"
  dnsStatus:       String,      // "pending" | "active" | "inactive" | "error"
  skipReason:      Option[String], // "whitelisted" | "invalid_format" | None
  statusLabel:     String,      // human-readable: "Pending Validation" | "Whitelisted" | "Invalid Format"
  createdAt:       String       // formatted for display
)
```

### `PermutationIntakeFormData`

```scala
final case class PermutationIntakeFormData(assetId: Long)
```

### `DiscoveryFormData`

```scala
final case class DiscoveryFormData(
  entityId: Long,
  assetId:  Option[Long],
  value:    String
)
```
