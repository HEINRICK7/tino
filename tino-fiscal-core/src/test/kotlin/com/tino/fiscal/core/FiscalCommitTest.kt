package com.tino.fiscal.core

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FiscalCommitTest {
    private val document = assertIs<FiscalParseResult.Success>(
        FiscalXmlParser().parse(
            javaClass.getResourceAsStream("/fixture-nfe-purchase-001.xml")!!.readBytes(),
        ),
    ).document

    private val supplier = FiscalSupplierCandidate(
        id = "supplier",
        taxId = document.issuer.taxId,
        legalName = document.issuer.legalName,
        tradeName = document.issuer.tradeName,
    )

    private fun previewWithExistingProducts() = FiscalImportPreviewBuilder().build(
        document = document,
        suppliers = listOf(supplier),
        products = listOf(
            FiscalProductCandidate("coffee", "Café Maratá 250g", "7891234567890", "UN", BigDecimal.ZERO),
            FiscalProductCandidate("rice", "Arroz Teste 5kg", null, "FD", BigDecimal.ZERO),
        ),
    )

    @Test
    fun validatesConfirmedExistingImportAndPreservesExactCosts() {
        val result = FiscalImportCommitValidator().validate(
            document = document,
            preview = previewWithExistingProducts(),
            confirmation = FiscalImportConfirmation(
                operationId = "op-existing",
                confirmedAt = Instant.parse("2026-08-18T12:00:00Z"),
                humanConfirmed = true,
                supplier = FiscalSupplierCommitDecision.UseExisting("supplier"),
                items = listOf(
                    FiscalItemCommitDecision.UseExisting(1, "coffee", BigDecimal("24")),
                    FiscalItemCommitDecision.UseExisting(2, "rice", BigDecimal("10")),
                ),
            ),
        )

        val valid = assertIs<FiscalCommitValidationResult.Valid>(result)
        assertEquals(20880L, valid.plan.invoiceTotalCents)
        assertEquals(620L, valid.plan.items[0].unitCostCents)
        assertEquals(24, valid.plan.items[0].stockQuantity)
        assertEquals(10, valid.plan.items[1].stockQuantity)
    }

    @Test
    fun newProductRequiresHumanSalePriceButCanBePlanned() {
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = emptyList(),
            products = listOf(
                FiscalProductCandidate("coffee", "Café Maratá 250g", "7891234567890", "UN", BigDecimal.ZERO),
            ),
        )

        val valid = assertIs<FiscalCommitValidationResult.Valid>(FiscalImportCommitValidator().validate(
            document = document,
            preview = preview,
            confirmation = FiscalImportConfirmation(
                operationId = "op-new",
                confirmedAt = Instant.parse("2026-08-18T12:00:00Z"),
                humanConfirmed = true,
                supplier = FiscalSupplierCommitDecision.Create(
                    legalName = document.issuer.legalName,
                    tradeName = document.issuer.tradeName,
                    taxId = document.issuer.taxId,
                ),
                items = listOf(
                    FiscalItemCommitDecision.UseExisting(1, "coffee", BigDecimal("24")),
                    FiscalItemCommitDecision.CreateProduct(
                        lineNumber = 2,
                        productName = "Arroz Teste 5kg",
                        salePriceCents = 1000,
                        inventoryUnit = "FD",
                        stockQuantity = BigDecimal("10"),
                    ),
                ),
            ),
        ))

        assertEquals("Arroz Teste 5kg", valid.plan.items[1].newProductName)
        assertEquals(1000L, valid.plan.items[1].newProductSalePriceCents)
    }

    @Test
    fun ambiguousProductCannotBeCommittedEvenWithASelectionAttempt() {
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = listOf(supplier),
            products = listOf(
                FiscalProductCandidate("coffee-a", "Café A", "7891234567890", "UN", BigDecimal.ZERO),
                FiscalProductCandidate("coffee-b", "Café B", "7891234567890", "UN", BigDecimal.ZERO),
                FiscalProductCandidate("rice", "Arroz Teste 5kg", null, "FD", BigDecimal.ZERO),
            ),
        )

        val rejected = assertIs<FiscalCommitValidationResult.Rejected>(FiscalImportCommitValidator().validate(
            document = document,
            preview = preview,
            confirmation = existingConfirmation("op-ambiguous", "coffee-a", "rice"),
        ))

        assertTrue(rejected.reasons.any { it == "PREVIEW_NOT_COMMITTABLE" })
        assertTrue(rejected.reasons.any { it == "AMBIGUOUS_PRODUCT:1" })
    }

    @Test
    fun packagingRequiredCannotBeConvertedByCommitValidator() {
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = listOf(supplier),
            products = listOf(
                FiscalProductCandidate("coffee", "Café Maratá 250g", "7891234567890", "CX", BigDecimal.ZERO),
                FiscalProductCandidate("rice", "Arroz Teste 5kg", null, "FD", BigDecimal.ZERO),
            ),
        )

        val rejected = assertIs<FiscalCommitValidationResult.Rejected>(FiscalImportCommitValidator().validate(
            document = document,
            preview = preview,
            confirmation = existingConfirmation("op-packaging", "coffee", "rice"),
        ))

        assertTrue(rejected.reasons.any { it == "PACKAGING_CONFIRMATION_REQUIRED:1" })
    }

    @Test
    fun mutationRequiresHumanConfirmationAndSeparatesPayable() {
        val rejected = assertIs<FiscalCommitValidationResult.Rejected>(FiscalImportCommitValidator().validate(
            document = document,
            preview = previewWithExistingProducts(),
            confirmation = existingConfirmation("op-no-human", "coffee", "rice").copy(
                humanConfirmed = false,
                payableConfirmed = true,
            ),
        ))

        assertTrue("HUMAN_CONFIRMATION_REQUIRED" in rejected.reasons)
        assertTrue("PAYABLE_COMMIT_NOT_SUPPORTED_IN_THIS_SLICE" in rejected.reasons)
    }

    private fun existingConfirmation(
        operationId: String,
        coffeeId: String,
        riceId: String,
    ) = FiscalImportConfirmation(
        operationId = operationId,
        confirmedAt = Instant.parse("2026-08-18T12:00:00Z"),
        humanConfirmed = true,
        supplier = FiscalSupplierCommitDecision.UseExisting("supplier"),
        items = listOf(
            FiscalItemCommitDecision.UseExisting(1, coffeeId, BigDecimal("24")),
            FiscalItemCommitDecision.UseExisting(2, riceId, BigDecimal("10")),
        ),
    )
}
