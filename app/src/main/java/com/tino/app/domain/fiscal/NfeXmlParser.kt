package com.tino.app.domain.fiscal

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

@Singleton
class NfeXmlParser @Inject constructor() {
    fun parse(xml: String, source: String = "xml"): ParsedFiscalDocument {
        require(xml.isNotBlank()) { "XML fiscal vazio." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        val root = document.documentElement
        val accessKey = root.getElementsByTagName("infNFe").item(0)
            ?.attributes?.getNamedItem("Id")?.nodeValue?.removePrefix("NFe")
        val supplier = text(document, "xNome") ?: error("Fornecedor ausente na NF-e.")
        val totalCents = decimal(document, "vNF").toCents()
        val items = (0 until document.getElementsByTagName("det").length).map { index ->
            val det = document.getElementsByTagName("det").item(index)
            val product = (det as org.w3c.dom.Element).getElementsByTagName("prod").item(0)
                ?: error("Item fiscal sem grupo prod.")
            FiscalLineItem(
                productCode = childText(product, "cProd"),
                barcode = childText(product, "cEAN")?.takeUnless { it == "SEM GTIN" },
                description = childText(product, "xProd") ?: error("Produto fiscal sem descrição."),
                ncm = childText(product, "NCM"),
                unit = childText(product, "uCom") ?: "un",
                quantity = childDecimal(product, "qCom"),
                unitCostCents = childDecimal(product, "vUnCom").toCents(),
            )
        }
        return ParsedFiscalDocument(accessKey, supplier, totalCents, items, source, xml)
    }

    private fun text(document: org.w3c.dom.Document, tag: String): String? =
        document.getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifBlank { null }

    private fun decimal(document: org.w3c.dom.Document, tag: String): BigDecimal =
        text(document, tag)?.toBigDecimal() ?: BigDecimal.ZERO

    private fun childText(node: org.w3c.dom.Node, tag: String): String? = node.childNodes
        .let { children -> (0 until children.length).asSequence().map { children.item(it) } }
        .firstOrNull { it.nodeName == tag }
        ?.textContent?.trim()?.ifBlank { null }

    private fun childDecimal(node: org.w3c.dom.Node, tag: String): BigDecimal =
        childText(node, tag)?.toBigDecimal() ?: BigDecimal.ZERO
}

private fun BigDecimal.toCents(): Long = movePointRight(2).longValueExact()
