package com.tino.fiscal.core

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FiscalMatchingTest {
    private val document = assertIs<FiscalParseResult.Success>(
        FiscalXmlParser().parse(
            javaClass.getResourceAsStream("/fixture-nfe-purchase-001.xml")!!.readBytes(),
        ),
    ).document

    @Test
    fun resolvesSupplierByExactTaxIdBeforeName() {
        val supplier = FiscalSupplierResolver().resolve(
            issuer = document.issuer,
            suppliers = listOf(
                FiscalSupplierCandidate("wrong", "12345678000194", "Outro Nome", null),
                FiscalSupplierCandidate("real", "12.345.678/0001-95", "Nome Local Diferente", null),
            ),
        )

        val resolved = assertIs<SupplierResolution.Resolved>(supplier)
        assertEquals("real", resolved.supplier.id)
        assertEquals(SupplierMatchMethod.EXACT_TAX_ID, resolved.method)
    }

    @Test
    fun doesNotSilentlyChooseAmbiguousSupplierName() {
        val supplier = FiscalSupplierResolver().resolve(
            issuer = document.issuer.copy(taxId = null, tradeName = null),
            suppliers = listOf(
                FiscalSupplierCandidate("one", null, "Distribuidora Teste LTDA", null),
                FiscalSupplierCandidate("two", null, "DISTRIBUIDORA TESTE LTDA", null),
            ),
        )

        val ambiguous = assertIs<SupplierResolution.Ambiguous>(supplier)
        assertEquals(setOf("one", "two"), ambiguous.candidates.map { it.id }.toSet())
    }

    @Test
    fun taxIdMismatchDoesNotBecomeAnAutomaticNameMatch() {
        val supplier = FiscalSupplierResolver().resolve(
            issuer = document.issuer,
            suppliers = listOf(
                FiscalSupplierCandidate("name-only", "00000000000000", "DISTRIBUIDORA TESTE LTDA", null),
            ),
        )

        val ambiguous = assertIs<SupplierResolution.Ambiguous>(supplier)
        assertEquals(listOf("name-only"), ambiguous.candidates.map { it.id })
    }

    @Test
    fun matchesProductByGtinAndThenBySupplierMapping() {
        val products = listOf(
            FiscalProductCandidate("coffee", "Café Maratá 250g", "7891234567890", "UN", BigDecimal("3")),
            FiscalProductCandidate("rice", "Arroz Teste 5kg", null, "UN", BigDecimal("0")),
        )
        val supplier = SupplierResolution.Resolved(
            FiscalSupplierCandidate("supplier", document.issuer.taxId, "Distribuidora", null),
            SupplierMatchMethod.EXACT_TAX_ID,
        )

        val coffee = FiscalProductMatcher().resolve(
            item = document.items[0],
            supplier = supplier,
            products = products,
            mappings = emptyList(),
            aliases = emptyList(),
        )
        assertEquals(ProductMatchMethod.EXACT_GTIN, assertIs<ProductResolution.Resolved>(coffee).method)

        val rice = FiscalProductMatcher().resolve(
            item = document.items[1],
            supplier = supplier,
            products = products,
            mappings = listOf(
                SupplierProductMapping(
                    supplierId = "supplier",
                    supplierProductCode = "ARZ002",
                    gtin = null,
                    supplierDescription = "Arroz Teste 5kg",
                    productId = "rice",
                    confirmedAt = Instant.parse("2026-08-18T12:00:00Z"),
                    matchMethod = ProductMatchMethod.SUPPLIER_PRODUCT_MAPPING,
                ),
            ),
            aliases = emptyList(),
        )
        assertEquals(
            ProductMatchMethod.SUPPLIER_PRODUCT_MAPPING,
            assertIs<ProductResolution.Resolved>(rice).method,
        )
    }

    @Test
    fun normalizesDescriptionAndNeverFillsMissingGtin() {
        val item = document.items[0].copy(gtin = null, description = "CAFE MARATA 250G")
        val result = FiscalProductMatcher().resolve(
            item = item,
            supplier = SupplierResolution.NotFound,
            products = listOf(
                FiscalProductCandidate("coffee", "Café Maratá 250g", "7891234567890", "UN", BigDecimal.ZERO),
            ),
            mappings = emptyList(),
            aliases = emptyList(),
        )

        val resolved = assertIs<ProductResolution.Resolved>(result)
        assertEquals(ProductMatchMethod.NORMALIZED_DESCRIPTION, resolved.method)
        assertEquals("coffee", resolved.product.id)
        assertEquals(null, item.gtin)
    }

    @Test
    fun lowConfidenceFuzzyMatchBecomesNotFound() {
        val result = FiscalProductMatcher().resolve(
            item = document.items[0].copy(gtin = null, supplierProductCode = null, description = "produto azul"),
            supplier = SupplierResolution.NotFound,
            products = listOf(
                FiscalProductCandidate("coffee", "Café Maratá 250g", null, "UN", BigDecimal.ZERO),
            ),
            mappings = emptyList(),
            aliases = emptyList(),
        )

        assertIs<ProductResolution.NotFound>(result)
    }

    @Test
    fun duplicateGtinIsAmbiguousInsteadOfChoosingFirstProduct() {
        val result = FiscalProductMatcher().resolve(
            item = document.items[0],
            supplier = SupplierResolution.NotFound,
            products = listOf(
                FiscalProductCandidate("coffee-a", "Café Maratá 250g", "7891234567890", "UN", BigDecimal.ZERO),
                FiscalProductCandidate("coffee-b", "Café Maratá 250g", "7891234567890", "UN", BigDecimal.ZERO),
            ),
            mappings = emptyList(),
            aliases = emptyList(),
        )

        val ambiguous = assertIs<ProductResolution.Ambiguous>(result)
        assertEquals(setOf("coffee-a", "coffee-b"), ambiguous.candidates.map { it.product.id }.toSet())
    }

    @Test
    fun previewExposesNewAndAmbiguousItemsWithoutEnablingCommit() {
        val preview = FiscalImportPreviewBuilder().build(
            document = document,
            suppliers = emptyList(),
            products = listOf(
                FiscalProductCandidate("coffee-a", "Cafe Marata 250g", "7891234567890", "UN", BigDecimal.ZERO),
            ),
        )

        assertIs<FiscalImportSupplierPreview.NewSupplier>(preview.supplier)
        assertIs<FiscalItemImportPreview.ExistingProduct>(preview.items[0])
        assertIs<FiscalItemImportPreview.NewProduct>(preview.items[1])
        assertTrue(FiscalImportWarning.NEW_SUPPLIER_REQUIRES_CONFIRMATION in preview.warnings)
        assertTrue(FiscalImportWarning.NEW_PRODUCT_REQUIRES_CONFIRMATION in preview.warnings)
        assertTrue(preview.canCommit)
    }
}
