package com.tino.app.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceExtractionValidatorTest {
    @Test
    fun onboardingNormalizesPhoneAndIgnoresFieldsOutsideContext() {
        val result = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.ONBOARDING,
                transcript = "Vou cadastrar meu comércio",
                fields = mapOf(
                    "store_name" to "  Mercadinho   São José ",
                    "owner_name" to " João da Silva ",
                    "phone" to "(86) 9 1234-5678",
                    "sale_price" to "19,90",
                ),
            ),
        )

        val valid = result as VoiceValidationResult.Valid
        assertEquals("Mercadinho São José", valid.value.fields["store_name"])
        assertEquals("João da Silva", valid.value.fields["owner_name"])
        assertEquals("86912345678", valid.value.fields["phone"])
        assertEquals(setOf("sale_price"), valid.ignoredFields)
    }

    @Test
    fun missingAndInvalidFieldsBecomeActionableCorrection() {
        val result = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.PRODUCT_CREATE,
                transcript = "Cadastrar produto",
                fields = mapOf(
                    "product_name" to "Café",
                    "sale_price" to "R$ 0,00",
                    "unit" to "pacote",
                ),
            ),
        )

        val correction = result as VoiceValidationResult.NeedsCorrection
        assertTrue("preço de venda" in correction.message)
        assertEquals(setOf("sale_price"), correction.invalidFields)
        assertEquals(emptySet<String>(), correction.missingFields)
        assertEquals("Café", correction.value.fields["product_name"])
        assertEquals("pacote", correction.value.fields["unit"])
    }

    @Test
    fun requiredFieldMissingIsReportedWithoutApplyingInvalidValue() {
        val result = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.ONBOARDING,
                transcript = "Cadastrar",
                fields = mapOf(
                    "store_name" to "Mercadinho",
                    "phone" to "123",
                ),
            ),
        )

        val correction = result as VoiceValidationResult.NeedsCorrection
        assertEquals(setOf("owner_name"), correction.missingFields)
        assertEquals(setOf("phone"), correction.invalidFields)
        assertTrue("seu nome" in correction.message)
        assertTrue("celular" in correction.message)
        assertEquals(null, correction.value.fields["phone"])
    }

    @Test
    fun productMoneyAndStockQuantityAreCanonicalized() {
        val product = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.PRODUCT_CREATE,
                transcript = "Cadastrar café",
                fields = mapOf("product_name" to "Café", "sale_price" to "R$ 1.234,5"),
            ),
        ) as VoiceValidationResult.Valid
        assertEquals("1234.50", product.value.fields["sale_price"])

        val stock = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.STOCK_RECEIPT,
                transcript = "Receber café",
                fields = mapOf(
                    "product" to "Café",
                    "quantity" to " 12 ",
                    "unit_cost" to "R$ 4,5",
                ),
            ),
        ) as VoiceValidationResult.Valid
        assertEquals("12", stock.value.fields["quantity"])
        assertEquals("4.50", stock.value.fields["unit_cost"])
    }

    @Test
    fun contextualVoiceAcceptsAdditionalProductAndSupplierFields() {
        val product = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.PRODUCT_CREATE,
                transcript = "Cadastrar café com estoque 12",
                fields = mapOf(
                    "product_name" to "Café",
                    "sale_price" to "5,50",
                    "stock_initial" to "12",
                ),
            ),
        ) as VoiceValidationResult.Valid
        assertEquals("12", product.value.fields["stock_initial"])

        val stock = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.STOCK_RECEIPT,
                transcript = "Chegou café do fornecedor",
                fields = mapOf(
                    "product" to "Café",
                    "quantity" to "12",
                    "supplier" to "Distribuidora Central",
                ),
            ),
        ) as VoiceValidationResult.Valid
        assertEquals("Distribuidora Central", stock.value.fields["supplier"])
    }

    @Test
    fun creditSaleSelectionAcceptsCustomerBeforeProductIsChosen() {
        val credit = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.CREDIT_SALE,
                transcript = "É para João",
                fields = mapOf("customer" to "João"),
            ),
        ) as VoiceValidationResult.Valid

        assertEquals("João", credit.value.fields["customer"])
        assertTrue("products" !in credit.value.fields)
    }

    @Test
    fun quickSaleVoiceCanUseProductWithoutPaymentYet() {
        val sale = VoiceExtractionValidator.validate(
            VoiceExtraction(
                context = VoiceContext.SALE,
                transcript = "Quero dois cafés",
                fields = mapOf("products" to "Café", "quantity" to "2"),
            ),
        ) as VoiceValidationResult.Valid

        assertEquals("Café", sale.value.fields["products"])
        assertEquals("2", sale.value.fields["quantity"])
    }
}
