package com.tino.app.core.sync

import com.tino.app.core.database.CreditEntryEntity
import com.tino.app.core.database.CreditEntryType
import com.tino.app.core.database.CustomerEntity
import com.tino.app.core.database.DomainEventEntity
import com.tino.app.core.database.DirectReceiptEntity
import com.tino.app.core.database.FiscalImportEntity
import com.tino.app.core.database.FiscalImportStatus
import com.tino.app.core.database.ProductPurchaseHistoryEntity
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.PurchaseEntity
import com.tino.app.core.database.PurchaseItemEntity
import com.tino.app.core.database.PurchaseStatus
import com.tino.app.core.database.SaleEntity
import com.tino.app.core.database.SaleItemEntity
import com.tino.app.core.database.StockMovementEntity
import com.tino.app.core.database.SupplierEntity
import com.tino.app.core.database.SupplierProductMappingEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.observability.NoOpAuditLogger
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteEventApplier @Inject constructor(
    private val database: TinoDatabase,
    private val auditLogger: AuditLogger = NoOpAuditLogger,
) {
    suspend fun applyIfNew(event: DomainEventEntity) {
        if (database.domainEventDao().exists(event.eventId)) {
            auditLogger.record(
                AuditEventType.SYNC_STATUS,
                mapOf("sync_state" to "duplicate_ignored"),
            )
            return
        }
        val payload = JSONObject(event.payloadJson)
        val supported = when (event.type) {
            "product.created" -> { applyProduct(event, payload); true }
            "customer.created" -> { applyCustomer(event, payload); true }
            "customer.updated" -> { applyCustomerUpdate(event, payload); true }
            "supplier.created" -> { applySupplier(event, payload); true }
            "product.created.from_fiscal_document" -> { applyProduct(event, payload); true }
            "supplier.product.mapping.created" -> { applySupplierProductMapping(event, payload); true }
            "sale.created" -> { applySale(event, payload); true }
            "direct.receipt.created" -> { applyDirectReceipt(event, payload); true }
            "credit.receivable.created" -> { applyCreditReceivable(event, payload); true }
            "stock.received" -> { applyStockReceipt(event, payload); true }
            "inventory.purchase.received" -> { applyFiscalStockReceipt(event, payload); true }
            "credit.payment.received" -> { applyCreditPayment(event, payload); true }
            "credit.payment.reversed" -> { applyCreditPaymentReversal(event, payload); true }
            "credit.adjustment.created" -> { applyCreditAdjustment(event, payload); true }
            "credit.entry.disputed" -> { applyCreditDispute(event, payload); true }
            "credit.settled" -> { applyCreditSettlement(event, payload); true }
            "purchase.created", "purchase.ordered" -> { applyPurchase(event, payload); true }
            "purchase.received" -> { applyPurchaseReceived(event, payload); true }
            "fiscal.import.committed" -> { applyFiscalImportMarker(event, payload); true }
            // credit.sale.created is represented by the credit entry created with sale.created.
            else -> false
        }
        database.domainEventDao().insertIgnore(
            event.copy(
                syncStatus = if (supported) {
                    com.tino.app.core.database.SyncStatus.SYNCED
                } else {
                    com.tino.app.core.database.SyncStatus.BLOCKED
                },
                lastError = if (supported) null else "UNSUPPORTED_EVENT: ${event.type} v${event.schemaVersion}",
            ),
        )
    }

    private suspend fun applyProduct(event: DomainEventEntity, payload: JSONObject) {
        if (database.productDao().findById(event.aggregateId) == null) {
            database.productDao().insert(
                ProductEntity(
                    id = event.aggregateId,
                    name = payload.getString("name"),
                    priceCents = payload.getLong("price_cents"),
                    unit = payload.optString("unit", "un"),
                    createdAt = event.occurredAt,
                ),
            )
        }
    }

    private suspend fun applyCustomer(event: DomainEventEntity, payload: JSONObject) {
        if (database.customerDao().findById(event.aggregateId) == null) {
            database.customerDao().insert(
                CustomerEntity(
                    id = event.aggregateId,
                    name = payload.getString("name"),
                    phone = payload.optString("phone").ifBlank { null },
                    createdAt = event.occurredAt,
                ),
            )
        }
    }

    private suspend fun applyCustomerUpdate(event: DomainEventEntity, payload: JSONObject) {
        val existing = database.customerDao().findById(event.aggregateId)
        if (existing == null) {
            database.customerDao().insert(
                CustomerEntity(
                    id = event.aggregateId,
                    name = payload.getString("name"),
                    phone = payload.optString("phone").ifBlank { null },
                    createdAt = event.occurredAt,
                ),
            )
        } else {
            database.customerDao().updateProfile(
                id = existing.id,
                name = payload.getString("name"),
                phone = payload.optString("phone").ifBlank { null },
            )
        }
    }

    private suspend fun applySupplier(event: DomainEventEntity, payload: JSONObject) {
        if (database.supplierDao().findById(event.aggregateId) == null) {
            database.supplierDao().insert(
                SupplierEntity(
                    id = event.aggregateId,
                    name = payload.getString("name"),
                    phone = payload.optString("phone").ifBlank { null },
                    createdAt = event.occurredAt,
                    taxId = payload.optString("tax_id").ifBlank { null },
                ),
            )
        }
    }

    private suspend fun applySale(event: DomainEventEntity, payload: JSONObject) {
        val saleId = event.aggregateId
        if (database.saleDao().findById(saleId) != null) return
        val paymentMethod = payload.optString("payment_method", "unknown")
        val items = payload.optJSONArray("items")
        val saleItems = if (items != null) {
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                SaleItemEntity(
                    saleId = saleId,
                    lineNumber = index,
                    productId = item.getString("product_id"),
                    quantity = item.getInt("quantity"),
                    unitPriceCents = item.getLong("unit_price_cents"),
                )
            }
        } else {
            listOf(
                SaleItemEntity(
                    saleId = saleId,
                    lineNumber = 0,
                    productId = payload.getString("product_id"),
                    quantity = payload.getInt("quantity"),
                    unitPriceCents = payload.optLong("unit_price_cents", 0L),
                ),
            )
        }
        database.saleDao().insert(
            SaleEntity(saleId, payload.getLong("total_cents"), paymentMethod, event.occurredAt),
        )
        database.saleDao().insertItems(saleItems)
        saleItems.forEachIndexed { index, item ->
            database.stockMovementDao().insert(
                StockMovementEntity("${event.eventId}:stock:$index", item.productId, -item.quantity, "remote_sale", saleId, event.occurredAt),
            )
        }
        if (paymentMethod == "credit") {
            val entryId = payload.optString("credit_entry_id").ifBlank { event.eventId }
            if (database.creditDao().findById(entryId) == null) {
                database.creditDao().insert(
                    CreditEntryEntity(
                        entryId,
                        payload.getString("customer_id"),
                        payload.getLong("total_cents"),
                        CreditEntryType.SALE,
                        saleId,
                        event.occurredAt,
                        "credit",
                        payload.optionalLong("due_at"),
                        payload.optString("ledger_type", "PURCHASE"),
                        payload.optString("provenance").ifBlank { null },
                    ),
                )
            }
        }
    }

    private suspend fun applyDirectReceipt(event: DomainEventEntity, payload: JSONObject) {
        val operationId = payload.optString("operation_id").ifBlank { event.aggregateId }
        if (database.directReceiptDao().findByOperationId(operationId) != null) return
        val receiptId = payload.optString("receipt_id").ifBlank { operationId }
        database.directReceiptDao().insert(
            DirectReceiptEntity(
                id = receiptId,
                amountCents = payload.getLong("amount_cents"),
                paymentMethod = payload.getString("payment_method"),
                occurredAt = event.occurredAt,
                source = payload.optString("source", "remote"),
                note = payload.optString("note").ifBlank { null },
                operationId = operationId,
            ),
        )
    }

    private suspend fun applyStockReceipt(event: DomainEventEntity, payload: JSONObject) {
        if (database.stockMovementDao().findById(event.eventId) == null) {
            database.stockMovementDao().insert(
                StockMovementEntity(
                    event.eventId,
                    payload.getString("product_id"),
                    payload.getInt("quantity"),
                    "remote_receipt",
                    payload.optString("purchase_id").ifBlank { null },
                    event.occurredAt,
                ),
            )
        }
    }

    private suspend fun applyFiscalStockReceipt(event: DomainEventEntity, payload: JSONObject) {
        if (database.stockMovementDao().findById(event.eventId) == null) {
            database.stockMovementDao().insert(
                StockMovementEntity(
                    id = event.eventId,
                    productId = payload.getString("product_id"),
                    quantityDelta = payload.getInt("quantity"),
                    reason = "fiscal_import_remote",
                    referenceId = payload.optString("purchase_id").ifBlank { null },
                    occurredAt = event.occurredAt,
                ),
            )
        }
        val historyId = payload.optString("history_id").ifBlank { "${event.eventId}:history" }
        if (database.productPurchaseHistoryDao().findById(historyId) == null) {
            database.productPurchaseHistoryDao().insert(
                ProductPurchaseHistoryEntity(
                    id = historyId,
                    fiscalDocumentId = payload.getString("fiscal_document_id"),
                    supplierId = payload.getString("supplier_id"),
                    productId = payload.getString("product_id"),
                    purchasedAt = event.occurredAt,
                    fiscalQuantity = payload.getString("fiscal_quantity"),
                    stockQuantity = payload.getInt("quantity"),
                    unitPurchaseCostCents = payload.getLong("unit_cost_cents"),
                    totalCostCents = payload.getLong("total_cost_cents"),
                ),
            )
        }
    }

    private suspend fun applySupplierProductMapping(event: DomainEventEntity, payload: JSONObject) {
        val mappingId = payload.getString("mapping_id")
        if (database.supplierProductMappingDao().findById(mappingId) != null) return
        database.supplierProductMappingDao().insert(
            SupplierProductMappingEntity(
                id = mappingId,
                supplierId = payload.getString("supplier_id"),
                supplierProductCode = payload.optString("supplier_product_code").ifBlank { null },
                gtin = payload.optString("gtin").ifBlank { null },
                supplierDescription = payload.getString("supplier_description"),
                productId = payload.getString("product_id"),
                confirmedAt = payload.optLong("confirmed_at", event.occurredAt),
                matchMethod = payload.optString("match_method", "REMOTE_CONFIRMED"),
            ),
        )
    }

    private suspend fun applyFiscalImportMarker(event: DomainEventEntity, payload: JSONObject) {
        val documentId = payload.getString("fiscal_document_id")
        if (database.fiscalImportDao().findByDocumentId(documentId) != null) return
        val operationId = payload.getString("operation_id")
        database.fiscalImportDao().insert(
            FiscalImportEntity(
                id = operationId,
                documentId = documentId,
                accessKey = payload.optString("access_key").ifBlank { null },
                documentHashSha256 = payload.getString("document_hash_sha256"),
                operationId = operationId,
                status = FiscalImportStatus.COMMITTED,
                committedAt = event.occurredAt,
                // Raw XML is deliberately not sent in sync event payloads.
                originalXml = ByteArray(0),
            ),
        )
    }

    private suspend fun applyCreditPayment(event: DomainEventEntity, payload: JSONObject) {
        val entryId = payload.optString("entry_id").ifBlank { event.eventId }
        if (database.creditDao().findById(entryId) == null) {
            database.creditDao().insert(
                CreditEntryEntity(
                    entryId,
                    payload.getString("customer_id"),
                    -payload.getLong("amount_cents"),
                    CreditEntryType.PAYMENT,
                    null,
                    event.occurredAt,
                    payload.optString("payment_method", "unknown"),
                    null,
                    payload.optString("ledger_type", "PAYMENT"),
                    payload.optString("provenance").ifBlank { null },
                    payload.optString("reason").ifBlank { null },
                ),
            )
        }
    }

    private suspend fun applyCreditPaymentReversal(event: DomainEventEntity, payload: JSONObject) {
        val entryId = payload.optString("compensation_entry_id").ifBlank { event.aggregateId }
        val originalPaymentId = payload.getString("original_payment_id")
        if (database.creditDao().findById(entryId) != null ||
            database.creditDao().findReversalByReference(originalPaymentId) != null
        ) return
        database.creditDao().insert(
            CreditEntryEntity(
                id = entryId,
                customerId = payload.getString("customer_id"),
                amountCents = payload.getLong("amount_cents"),
                type = CreditEntryType.SALE,
                referenceId = originalPaymentId,
                occurredAt = event.occurredAt,
                paymentMethod = "credit",
                ledgerType = payload.optString("ledger_type", "REVERSAL"),
                provenance = payload.optString("provenance").ifBlank { null },
                reason = payload.optString("reason").ifBlank { null },
            ),
        )
    }

    private suspend fun applyCreditReceivable(event: DomainEventEntity, payload: JSONObject) {
        val entryId = payload.optString("entry_id").ifBlank { event.aggregateId }
        if (database.creditDao().findById(entryId) != null) return
        database.creditDao().insert(
            CreditEntryEntity(
                id = entryId,
                customerId = payload.getString("customer_id"),
                amountCents = payload.getLong("amount_cents"),
                type = CreditEntryType.SALE,
                referenceId = null,
                occurredAt = event.occurredAt,
                paymentMethod = "credit",
                dueAt = payload.optionalLong("due_at"),
                ledgerType = payload.optString("ledger_type", "PURCHASE"),
                provenance = payload.optString("provenance").ifBlank { null },
            ),
        )
    }

    private suspend fun applyCreditAdjustment(event: DomainEventEntity, payload: JSONObject) {
        val entryId = payload.optString("entry_id").ifBlank { event.aggregateId }
        if (database.creditDao().findById(entryId) != null) return
        database.creditDao().insert(
            CreditEntryEntity(
                id = entryId,
                customerId = payload.getString("customer_id"),
                amountCents = payload.getLong("amount_cents"),
                type = CreditEntryType.SALE,
                referenceId = null,
                occurredAt = event.occurredAt,
                paymentMethod = "credit",
                ledgerType = payload.optString("ledger_type", "ADJUSTMENT"),
                provenance = payload.optString("provenance").ifBlank { null },
                reason = payload.optString("reason").ifBlank { null },
            ),
        )
    }

    private suspend fun applyCreditDispute(event: DomainEventEntity, payload: JSONObject) {
        val entryId = payload.optString("dispute_id").ifBlank { event.aggregateId }
        if (database.creditDao().findById(entryId) != null) return
        database.creditDao().insert(
            CreditEntryEntity(
                id = entryId,
                customerId = payload.getString("customer_id"),
                amountCents = 0,
                type = CreditEntryType.SALE,
                referenceId = payload.getString("entry_id"),
                occurredAt = event.occurredAt,
                paymentMethod = "credit",
                ledgerType = payload.optString("ledger_type", "DISPUTE"),
                provenance = payload.optString("provenance").ifBlank { null },
                reason = payload.optString("reason").ifBlank { null },
            ),
        )
    }

    private suspend fun applyCreditSettlement(event: DomainEventEntity, payload: JSONObject) {
        val entryId = payload.optString("entry_id").ifBlank { event.aggregateId }
        if (database.creditDao().findById(entryId) != null) return
        database.creditDao().insert(
            CreditEntryEntity(
                id = entryId,
                customerId = payload.getString("customer_id"),
                amountCents = -payload.getLong("amount_cents"),
                type = CreditEntryType.SALE,
                referenceId = null,
                occurredAt = event.occurredAt,
                paymentMethod = "credit",
                ledgerType = payload.optString("ledger_type", "SETTLEMENT"),
                provenance = payload.optString("provenance").ifBlank { null },
                reason = payload.optString("reason").ifBlank { null },
            ),
        )
    }

    private fun JSONObject.optionalLong(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0 } else null

    private suspend fun applyPurchaseReceived(event: DomainEventEntity, payload: JSONObject) {
        val purchase = database.purchaseDao().findById(event.aggregateId) ?: return
        if (purchase.status == PurchaseStatus.RECEIVED || purchase.status == PurchaseStatus.COMPLETED) return
        database.purchaseDao().updateStatus(
            purchaseId = event.aggregateId,
            status = PurchaseStatus.RECEIVED,
            receivedAt = payload.optionalLong("received_at") ?: event.occurredAt,
        )
    }

    private suspend fun applyPurchase(event: DomainEventEntity, payload: JSONObject) {
        if (database.purchaseDao().findById(event.aggregateId) != null) return
        val supplierId = payload.optString("supplier_id").ifBlank { null }
        database.purchaseDao().insert(
            PurchaseEntity(
                event.aggregateId,
                supplierId,
                PurchaseStatus.valueOf(payload.getString("status")),
                payload.getLong("total_cost_cents"),
                event.occurredAt,
                payload.optionalLong("expected_delivery_at"),
                payload.optionalLong("received_at")?.takeIf {
                    PurchaseStatus.valueOf(payload.getString("status")) == PurchaseStatus.RECEIVED ||
                        PurchaseStatus.valueOf(payload.getString("status")) == PurchaseStatus.COMPLETED
                },
            ),
        )
        val items = payload.optJSONArray("items")
        val purchaseItems = if (items != null) {
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                PurchaseItemEntity(
                    event.aggregateId,
                    index,
                    item.getString("product_id"),
                    item.getInt("quantity"),
                    item.getLong("unit_cost_cents"),
                )
            }
        } else {
            listOf(
                PurchaseItemEntity(
                    event.aggregateId,
                    0,
                    payload.getString("product_id"),
                    payload.getInt("quantity"),
                    payload.getLong("unit_cost_cents"),
                ),
            )
        }
        database.purchaseDao().insertItems(purchaseItems)
    }
}
