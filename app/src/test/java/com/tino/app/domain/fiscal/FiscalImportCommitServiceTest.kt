package com.tino.app.domain.fiscal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.SupplierEntity
import com.tino.app.core.database.TinoDatabase
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.core.sync.CommerceSnapshotRepository
import com.tino.app.core.sync.RemoteEventApplier
import com.tino.fiscal.core.CanonicalFiscalDocument
import com.tino.fiscal.core.CanonicalFiscalItem
import com.tino.fiscal.core.FiscalDocumentModel
import com.tino.fiscal.core.FiscalEvidence
import com.tino.fiscal.core.FiscalImportCommitValidator
import com.tino.fiscal.core.FiscalImportConfirmation
import com.tino.fiscal.core.FiscalImportPreviewBuilder
import com.tino.fiscal.core.FiscalItemCommitDecision
import com.tino.fiscal.core.FiscalOperationType
import com.tino.fiscal.core.FiscalParty
import com.tino.fiscal.core.FiscalProvenance
import com.tino.fiscal.core.FiscalSource
import com.tino.fiscal.core.FiscalSupplierCandidate
import com.tino.fiscal.core.FiscalSupplierCommitDecision
import com.tino.fiscal.core.FiscalProductCandidate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class FiscalImportCommitServiceTest {
    private lateinit var database: TinoDatabase
    private lateinit var service: FiscalImportCommitService
    private var scheduled = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = FiscalImportCommitService(
            database = database,
            fiscalImportDao = database.fiscalImportDao(),
            supplierDao = database.supplierDao(),
            purchaseDao = database.purchaseDao(),
            stockMovementDao = database.stockMovementDao(),
            supplierProductMappingDao = database.supplierProductMappingDao(),
            productPurchaseHistoryDao = database.productPurchaseHistoryDao(),
            identityProvider = IdentityProvider(context),
            syncScheduler = object : SyncScheduler {
                override fun schedule() { scheduled++ }
            },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun commitsExistingProductAtomicallyAndSecondImportDoesNotDoubleStock() = runBlocking {
        database.supplierDao().insert(SupplierEntity("supplier", "Distribuidora", null, 1L, "12345678000195"))
        database.productDao().insert(ProductEntity("coffee", "Café Maratá", 850, "UN", 1L))
        val document = CanonicalFixtureFactory.document("doc-existing")
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = listOf(FiscalSupplierCandidate("supplier", document.issuer.taxId, "Distribuidora", null)),
            products = listOf(FiscalProductCandidate("coffee", "Café Maratá", "7891234567890", "UN", BigDecimal.ZERO)),
        )
        val confirmation = existingConfirmation("operation-existing", "coffee")

        val first = service.commit(document, preview, confirmation)
        val second = service.commit(document, preview, confirmation)

        assertTrue(first is FiscalImportCommitResult.Committed && !first.alreadyCommitted)
        assertTrue(second is FiscalImportCommitResult.Committed && second.alreadyCommitted)
        assertEquals(2, database.stockMovementDao().balance("coffee"))
        assertEquals(1, database.purchaseDao().all().size)
        assertEquals(1, database.fiscalImportDao().all().size)
        assertEquals(1, database.productPurchaseHistoryDao().all().size)
        assertEquals(1, scheduled)
    }

    @Test
    fun commitsConfirmedNewSupplierAndProductWithoutChangingUnrelatedProducts() = runBlocking {
        database.productDao().insert(ProductEntity("existing", "Produto Existente", 900, "UN", 1L))
        val document = CanonicalFixtureFactory.document("doc-new")
        val preview = FiscalImportPreviewBuilder().build(document, emptyList(), emptyList())
        val confirmation = FiscalImportConfirmation(
            operationId = "operation-new",
            confirmedAt = Instant.parse("2026-08-18T12:00:00Z"),
            humanConfirmed = true,
            supplier = FiscalSupplierCommitDecision.Create(document.issuer.legalName, document.issuer.tradeName, document.issuer.taxId),
            items = listOf(
                FiscalItemCommitDecision.CreateProduct(1, "Café Maratá", 1000, "UN", BigDecimal("2")),
            ),
        )

        val result = service.commit(document, preview, confirmation)

        assertTrue(result is FiscalImportCommitResult.Committed)
        assertEquals(2, database.productDao().all().size)
        assertEquals(1, database.supplierDao().all().size)
        assertEquals(2, database.stockMovementDao().balance("fiscal-product:operation-new:1"))
        assertEquals(1000L, database.productDao().findById("fiscal-product:operation-new:1")?.priceCents)
    }

    @Test
    fun ambiguousPreviewIsRejectedBeforeAnyRoomWrite() = runBlocking {
        val document = CanonicalFixtureFactory.document("doc-rejected")
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = emptyList(),
            products = listOf(
                FiscalProductCandidate("one", "Café A", "7891234567890", "UN", BigDecimal.ZERO),
                FiscalProductCandidate("two", "Café B", "7891234567890", "UN", BigDecimal.ZERO),
            ),
        )

        val result = service.commit(document, preview, existingConfirmation("operation-rejected", "one"))

        assertTrue(result is FiscalImportCommitResult.Rejected)
        assertTrue(database.fiscalImportDao().all().isEmpty())
        assertTrue(database.purchaseDao().all().isEmpty())
        assertTrue(database.stockMovementDao().all().isEmpty())
    }

    @Test
    fun failureDuringSecondProductRollsBackSupplierProductPurchaseAndEvents() = runBlocking {
        database.supplierDao().insert(SupplierEntity("supplier", "Distribuidora", null, 1L, "12345678000195"))
        val document = CanonicalFixtureFactory.twoItemDocument("doc-rollback")
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = listOf(FiscalSupplierCandidate("supplier", document.issuer.taxId, "Distribuidora", null)),
            products = emptyList(),
        )
        val confirmation = FiscalImportConfirmation(
            operationId = "operation-rollback",
            confirmedAt = Instant.parse("2026-08-18T12:00:00Z"),
            humanConfirmed = true,
            supplier = FiscalSupplierCommitDecision.UseExisting("supplier"),
            items = listOf(
                FiscalItemCommitDecision.CreateProduct(1, "Mesmo Produto", 1000, "UN", BigDecimal("1")),
                FiscalItemCommitDecision.CreateProduct(2, "Mesmo Produto", 1000, "UN", BigDecimal("1")),
            ),
        )

        try {
            service.commit(document, preview, confirmation)
            throw AssertionError("expected Room constraint failure")
        } catch (_: Exception) {
            // The unique product-name violation must roll back the whole transaction.
        }

        assertEquals(0, database.productDao().all().size)
        assertEquals(1, database.supplierDao().all().size)
        assertTrue(database.purchaseDao().all().isEmpty())
        assertTrue(database.stockMovementDao().all().isEmpty())
        assertTrue(database.fiscalImportDao().all().isEmpty())
        assertTrue(database.domainEventDao().all().isEmpty())
    }

    @Test
    fun snapshotAndRemoteReplayPreserveFiscalOperationalState() = runBlocking {
        val document = CanonicalFixtureFactory.document("doc-sync")
        val preview = FiscalImportPreviewBuilder().build(document, emptyList(), emptyList())
        val confirmation = FiscalImportConfirmation(
            operationId = "operation-sync",
            confirmedAt = Instant.parse("2026-08-18T12:00:00Z"),
            humanConfirmed = true,
            supplier = FiscalSupplierCommitDecision.Create(document.issuer.legalName, document.issuer.tradeName, document.issuer.taxId),
            items = listOf(
                FiscalItemCommitDecision.CreateProduct(1, "Café Maratá", 1000, "UN", BigDecimal("2")),
            ),
        )
        service.commit(document, preview, confirmation)

        val snapshot = CommerceSnapshotRepository(database).export()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val restored = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val replayed = Room.inMemoryDatabaseBuilder(context, TinoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            CommerceSnapshotRepository(restored).restore(snapshot)
            assertEquals(1, restored.fiscalImportDao().all().size)
            assertEquals(1, restored.productPurchaseHistoryDao().all().size)
            assertEquals("<doc-sync/>", String(restored.fiscalImportDao().all().single().originalXml))

            val applier = RemoteEventApplier(replayed)
            database.domainEventDao().all().forEach { applier.applyIfNew(it) }
            assertEquals(1, replayed.productDao().all().size)
            assertEquals(1, replayed.supplierDao().all().size)
            assertEquals(2, replayed.stockMovementDao().balance("fiscal-product:operation-sync:1"))
            assertEquals(1, replayed.purchaseDao().all().size)
            assertEquals(1, replayed.fiscalImportDao().all().size)
            assertEquals(1, replayed.productPurchaseHistoryDao().all().size)
        } finally {
            restored.close()
            replayed.close()
        }
    }

    private fun existingConfirmation(operationId: String, productId: String) = FiscalImportConfirmation(
        operationId = operationId,
        confirmedAt = Instant.parse("2026-08-18T12:00:00Z"),
        humanConfirmed = true,
        supplier = FiscalSupplierCommitDecision.UseExisting("supplier"),
        items = listOf(FiscalItemCommitDecision.UseExisting(1, productId, BigDecimal("2"))),
    )
}

private object CanonicalFixtureFactory {
    fun document(id: String) = CanonicalFiscalDocument(
        id = id,
        accessKey = "access-$id",
        model = FiscalDocumentModel.NFE,
        number = "1",
        series = "1",
        issuedAt = Instant.parse("2026-08-10T17:20:00Z"),
        operationType = FiscalOperationType.ENTRY,
        issuer = FiscalParty("12345678000195", null, "DISTRIBUIDORA TESTE LTDA", "DISTRIBUIDORA TESTE", null),
        recipient = null,
        items = listOf(item(1, "Café Maratá", "CAF001", "7891234567890")),
        totals = com.tino.fiscal.core.FiscalTotals(BigDecimal("12.40"), null, null, null, BigDecimal("12.40")),
        installments = emptyList(),
        evidence = evidence(id),
    )

    fun twoItemDocument(id: String) = document(id).copy(
        items = listOf(item(1, "Mesmo Produto", "A", null), item(2, "Mesmo Produto", "B", null)),
        totals = com.tino.fiscal.core.FiscalTotals(BigDecimal("20.00"), null, null, null, BigDecimal("20.00")),
    )

    private fun item(line: Int, description: String, code: String, gtin: String?) = CanonicalFiscalItem(
        lineNumber = line,
        supplierProductCode = code,
        description = description,
        gtin = gtin,
        ncm = "09012100",
        cfop = "2102",
        commercialUnit = "UN",
        quantity = BigDecimal("2"),
        unitValue = BigDecimal("6.20"),
        totalValue = BigDecimal("12.40"),
        taxes = null,
        provenance = evidence("item-$line").provenance,
    )

    private fun evidence(id: String) = FiscalEvidence(
        originalXml = "<$id/>".toByteArray(),
        provenance = FiscalProvenance(FiscalSource.PROVIDED_XML, "hash-$id", "test"),
    )
}
