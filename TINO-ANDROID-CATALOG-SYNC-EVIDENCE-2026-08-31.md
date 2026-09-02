# TINO Android — Catalog Sync Evidence

## Implemented

- `CatalogApi` calls only the configurable TINO backend URL using the existing HTTPS transport and bearer-token abstraction.
- The response is mapped from the authoritative `product_id`, `name`, `base_unit`, `gtin` and decimal `price` fields.
- Price remains textual until `BigDecimal` validation and is stored as exact `priceCents: Long`.
- Invalid prices (null, negative, non-numeric, NaN/Infinity and fractional cent) are rejected without mutating Room.
- Product identity is `product_id`; an existing product is updated, a missing product is inserted, and absent backend products are not deleted.
- A local name conflict rejects only that item; it never merges or replaces an existing product row.
- Product upserts run in a Room transaction and preserve stock movements.
- Sync is serialized per app process, and the local sync state records status, counts, partial-query warning and sanitized failure text.
- Products continues to observe Room. The user starts the operation with `ATUALIZAR CATÁLOGO`; no network call is made from a Composable.

## Persistence

Migration `27 -> 28` is additive only:

- `products.gtin TEXT`;
- `catalog_sync_state` keyed by `businessId`.

No stock quantity column, movement, fiscal record or local product identity is changed.

## Contract boundaries

The backend endpoint is queried with `q`, `gtin` and `limit` (`1..100`). The Android side does not invent pagination; a response reaching the limit is marked as possibly partial and is not presented as a complete catalog. Direct Doces & Sonhos access and credentials are absent.

The current auth abstraction has a bearer token and stable 401 error handling, but no refresh-token API. The catalog sync therefore reuses that existing behavior and does not invent a refresh flow.

## Verification

- `SyncCatalogTest`: exact decimal conversion, malformed/fractional prices, item rejection, idempotent create/update and sanitized backend failure.
- `CatalogApiContractTest`: snake_case mapping, nullable GTIN, query construction and limit guard.
- `RoomCatalogProductStoreTest`: create/update, GTIN persistence, name conflict rejection and stock preservation.
- `CatalogMigrationTest`: additive migration and preservation of an existing product row.
