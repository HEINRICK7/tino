package com.tino.app.domain.nfce

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SefazPiNfceParserTest {
    private val html = javaClass.getResourceAsStream("/fixtures/sefaz-pi-nfce-result.html")!!
        .bufferedReader()
        .use { it.readText() }

    @Test
    fun parsesRealSefazPiFixtureIntoPurchaseDocument() {
        val document = SefazPiNfceParser().parse(html)

        assertEquals(PurchaseDocument.Source.NFCE, document.source)
        assertEquals(PurchaseDocument.DocumentType.NFCE, document.documentType)
        assertEquals("22260831838128000748650120002104021782591975", document.accessKey)
        assertEquals("GRUPO VANGUARDA", document.issuer.name)
        assertEquals("31.838.128/0007-48", document.issuer.taxId)
        assertEquals(6, document.items.size)
        assertEquals(1, document.items.first().lineNumber)
        assertEquals("249886", document.items.first().externalCode)
        assertEquals("QUEIJO MUSS ISIS 150G FAT", document.items.first().description)
        assertNull(document.items.first().gtin)
        assertEquals(BigDecimal("10.790"), document.items.first().unitPrice)
        assertEquals(BigDecimal("65.11"), document.total)
        assertEquals("2026-08-29T08:04:14", document.issuedAt.toString())
        assertTrue(document.items.all { it.description.isNotBlank() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonResultHtml() {
        SefazPiNfceParser().parse("<html><body>Resolva o hCaptcha</body></html>")
    }
}
