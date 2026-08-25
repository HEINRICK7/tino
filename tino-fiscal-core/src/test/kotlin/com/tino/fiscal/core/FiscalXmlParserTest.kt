package com.tino.fiscal.core

import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FiscalXmlParserTest {
    private val xml = javaClass.getResourceAsStream("/fixture-nfe-purchase-001.xml")!!
        .readBytes()

    @Test
    fun parsesSanitizedPurchaseIntoCanonicalDocument() {
        val result = assertIs<FiscalParseResult.Success>(FiscalXmlParser().parse(xml))
        val document = result.document

        assertEquals("nfe:35260812345678000195550010000000011000000018", document.id)
        assertEquals("35260812345678000195550010000000011000000018", document.accessKey)
        assertEquals(FiscalDocumentModel.NFE, document.model)
        assertEquals("1", document.number)
        assertEquals("1", document.series)
        assertEquals(Instant.parse("2026-08-10T17:20:00Z"), document.issuedAt)
        assertEquals(FiscalOperationType.ENTRY, document.operationType)
        assertEquals("DISTRIBUIDORA TESTE LTDA", document.issuer.legalName)
        assertEquals("98765432000100", document.recipient?.taxId)
        assertEquals(2, document.items.size)
        assertEquals(1, document.items[0].lineNumber)
        assertEquals("CAF001", document.items[0].supplierProductCode)
        assertEquals("7891234567890", document.items[0].gtin)
        assertEquals("Café Maratá 250g", document.items[0].description)
        assertEquals("09012100", document.items[0].ncm)
        assertEquals("2102", document.items[0].cfop)
        assertEquals("UN", document.items[0].commercialUnit)
        assertEquals(BigDecimal("24.0000"), document.items[0].quantity)
        assertEquals(BigDecimal("6.20"), document.items[0].unitValue)
        assertEquals(BigDecimal("148.80"), document.items[0].totalValue)
        assertEquals(BigDecimal("198.80"), document.totals.productsValue)
        assertEquals(BigDecimal("208.80"), document.totals.invoiceValue)
        assertEquals(1, document.installments.size)
        assertEquals("2026-09-10", document.installments.single().dueDate.toString())
        assertEquals(FiscalSource.PROVIDED_XML, document.evidence.provenance.source)
    }

    @Test
    fun preservesEvidenceHashAndDoesNotInventMissingGtin() {
        val result = assertIs<FiscalParseResult.Success>(FiscalXmlParser().parse(xml))
        val document = result.document
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(xml)
            .joinToString("") { "%02x".format(it) }

        assertContentEquals(xml, document.evidence.originalXml)
        assertEquals(expectedHash, document.evidence.provenance.documentHashSha256)
        assertEquals(64, document.evidence.provenance.documentHashSha256.length)
        assertNull(document.items[1].gtin)
        assertEquals("FD", document.items[1].commercialUnit)
    }

    @Test
    fun rejectsDoctypeToPreventExternalEntityResolution() {
        val unsafe = """
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <nfeProc><NFe><infNFe Id="NFe1"><ide><mod>55</mod></ide><emit><xNome>&xxe;</xNome></emit></infNFe></NFe></nfeProc>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val failure = assertIs<FiscalParseResult.Failure>(FiscalXmlParser().parse(unsafe))
        assertEquals("XML_INVALID", failure.code)
    }

    @Test
    fun preservesTotalsWithoutAdjustingItemValues() {
        val result = assertIs<FiscalParseResult.Success>(FiscalXmlParser().parse(xml))
        val itemsTotal = result.document.items.fold(BigDecimal.ZERO) { total, item -> total + item.totalValue }

        assertEquals(BigDecimal("198.80"), itemsTotal)
        assertEquals(BigDecimal("198.80"), result.document.totals.productsValue)
        assertTrue(result.document.items.all { it.provenance.documentHashSha256.isNotBlank() })
    }
}
