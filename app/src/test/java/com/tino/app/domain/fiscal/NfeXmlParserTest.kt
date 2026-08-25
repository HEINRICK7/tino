package com.tino.app.domain.fiscal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfeXmlParserTest {
    @Test
    fun extractsProvenanceSupplierItemsAndTotalsWithoutExternalEntities() {
        val xml = """
            <nfeProc>
              <NFe>
                <infNFe Id="NFe35202608160000000000000000000000000000000000">
                  <emit><xNome>Distribuidora Nordeste</xNome></emit>
                  <det nItem="1"><prod>
                    <cProd>CAF001</cProd><cEAN>789000000001</cEAN><xProd>Café Maratá</xProd>
                    <NCM>09012100</NCM><uCom>UN</uCom><qCom>24.0000</qCom><vUnCom>8.50</vUnCom>
                  </prod></det>
                </infNFe>
                <total><ICMSTot><vNF>204.00</vNF></ICMSTot></total>
              </NFe>
            </nfeProc>
        """.trimIndent()

        val parsed = NfeXmlParser().parse(xml, source = "receipt.xml")

        assertEquals("35202608160000000000000000000000000000000000", parsed.accessKey)
        assertEquals("Distribuidora Nordeste", parsed.supplierName)
        assertEquals(20_400L, parsed.totalCents)
        assertEquals(1, parsed.items.size)
        assertEquals("789000000001", parsed.items.single().barcode)
        assertEquals(850L, parsed.items.single().unitCostCents)
        assertTrue(parsed.rawXml.contains("Café Maratá"))
    }

    @Test(expected = Exception::class)
    fun rejectsXmlWithDoctype() {
        NfeXmlParser().parse("<!DOCTYPE foo [ <!ENTITY xxe SYSTEM 'file:///etc/passwd'> ]><nfeProc>&xxe;</nfeProc>")
    }
}
