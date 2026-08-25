package com.tino.fiscal.core

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

class FiscalXmlParser(
    private val parserVersion: String = PARSER_VERSION,
) {
    fun parse(xml: ByteArray, source: FiscalSource = FiscalSource.PROVIDED_XML): FiscalParseResult {
        val hash = sha256(xml)
        val provenance = FiscalProvenance(source, hash, parserVersion)
        return runCatching {
            val factory = secureFactory()
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            val root = document.documentElement
            val infNfe = root.findDescendant("infNFe")
                ?: return FiscalParseResult.Failure("MISSING_INF_NFE", "NF-e sem infNFe")
            val ide = infNfe.child("ide")
                ?: return FiscalParseResult.Failure("MISSING_IDE", "NF-e sem identificação")
            val issuer = parseParty(infNfe.child("emit"))
                ?: return FiscalParseResult.Failure("MISSING_ISSUER", "NF-e sem emitente")
            val items = infNfe.children("det").mapIndexed { index, det ->
                parseItem(det, index + 1, provenance)
            }
            val accessKey = infNfe.attribute("Id")
                ?.removePrefix("NFe")
                ?.takeIf { it.isNotBlank() }
                ?: infNfe.findDescendant("chNFe")?.textValue()
            val id = accessKey?.let { "nfe:$it" } ?: "xml:$hash"
            CanonicalFiscalDocument(
                id = id,
                accessKey = accessKey,
                model = when (ide.textValue("mod")) {
                    "55" -> FiscalDocumentModel.NFE
                    else -> FiscalDocumentModel.UNKNOWN
                },
                number = ide.textValue("nNF"),
                series = ide.textValue("serie"),
                issuedAt = parseInstant(ide.textValue("dhEmi") ?: ide.textValue("dEmi")),
                operationType = when (ide.textValue("tpNF")) {
                    "0" -> FiscalOperationType.ENTRY
                    "1" -> FiscalOperationType.EXIT
                    else -> FiscalOperationType.UNKNOWN
                },
                issuer = issuer,
                recipient = parseParty(infNfe.child("dest")),
                items = items,
                totals = parseTotals(infNfe.child("total")),
                installments = parseInstallments(infNfe.child("cobr")),
                evidence = FiscalEvidence(xml.copyOf(), provenance),
            )
        }.fold(
            onSuccess = { FiscalParseResult.Success(it) },
            onFailure = { error ->
                FiscalParseResult.Failure(
                    code = "XML_INVALID",
                    message = error.message ?: "XML fiscal inválido",
                )
            },
        )
    }

    private fun parseParty(element: Element?): FiscalParty? {
        if (element == null) return null
        val taxId = element.textValue("CNPJ") ?: element.textValue("CPF")
        val legalName = element.textValue("xNome")
        val tradeName = element.textValue("xFant")
        val address = element.child("enderEmit")?.let(::parseAddress)
            ?: element.child("enderDest")?.let(::parseAddress)
        return FiscalParty(
            taxId = taxId,
            stateRegistration = element.textValue("IE"),
            legalName = legalName,
            tradeName = tradeName,
            address = address,
        )
    }

    private fun parseAddress(element: Element): FiscalAddress = FiscalAddress(
        street = element.textValue("xLgr"),
        number = element.textValue("nro"),
        neighborhood = element.textValue("xBairro"),
        city = element.textValue("xMun"),
        state = element.textValue("UF"),
        postalCode = element.textValue("CEP"),
    )

    private fun parseItem(
        det: Element,
        fallbackLineNumber: Int,
        provenance: FiscalProvenance,
    ): CanonicalFiscalItem {
        val product = det.child("prod")
            ?: throw IllegalArgumentException("item fiscal sem produto")
        val taxes = det.child("imposto")?.let(::parseTaxes)
        return CanonicalFiscalItem(
            lineNumber = det.attribute("nItem")?.toIntOrNull() ?: fallbackLineNumber,
            supplierProductCode = product.textValue("cProd"),
            description = product.textValue("xProd") ?: "",
            gtin = product.textValue("cEAN").toNullableFiscalCode(),
            ncm = product.textValue("NCM"),
            cfop = product.textValue("CFOP"),
            commercialUnit = product.textValue("uCom"),
            quantity = product.decimal("qCom") ?: BigDecimal.ZERO,
            unitValue = product.decimal("vUnCom") ?: BigDecimal.ZERO,
            totalValue = product.decimal("vProd") ?: BigDecimal.ZERO,
            taxes = taxes,
            provenance = provenance,
        )
    }

    private fun parseTaxes(element: Element): FiscalItemTaxes {
        fun taxValue(group: String, field: String): BigDecimal? =
            element.findDescendant(group)?.textValue(field)?.toBigDecimalOrNullExact()
        return FiscalItemTaxes(
            totalTaxValue = element.decimal("vTotTrib"),
            icmsValue = element.findDescendant("ICMS")?.findDescendant("vICMS")?.decimalValue(),
            pisValue = taxValue("PIS", "vPIS"),
            cofinsValue = taxValue("COFINS", "vCOFINS"),
        )
    }

    private fun parseTotals(total: Element?): FiscalTotals {
        val values = total?.child("ICMSTot")
        return FiscalTotals(
            productsValue = values?.decimal("vProd"),
            freightValue = values?.decimal("vFrete"),
            discountValue = values?.decimal("vDesc"),
            otherValue = values?.decimal("vOutro"),
            invoiceValue = values?.decimal("vNF"),
        )
    }

    private fun parseInstallments(cobr: Element?): List<FiscalInstallment> =
        cobr?.children("dup")?.map { dup ->
            FiscalInstallment(
                number = dup.textValue("nDup"),
                dueDate = dup.textValue("dVenc")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                value = dup.decimal("vDup"),
            )
        }.orEmpty()

    private fun secureFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun parseInstant(value: String?): Instant? = value?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(it) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    private fun String?.toNullableFiscalCode(): String? = this
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("SEM GTIN", ignoreCase = true) }

    private fun Element.decimal(name: String): BigDecimal? = textValue(name)?.toBigDecimalOrNullExact()

    private fun String.toBigDecimalOrNullExact(): BigDecimal? = runCatching { BigDecimal(trim()) }.getOrNull()

    private fun Element.decimalValue(): BigDecimal? = textContent.trim().toBigDecimalOrNullExact()

    private fun Element.textValue(name: String): String? = child(name)?.textValue()

    private fun Element.textValue(): String? = textContent.trim().takeIf { it.isNotEmpty() }

    private fun Element.attribute(name: String): String? = getAttribute(name).takeIf { it.isNotBlank() }

    private fun Element.child(name: String): Element? = (0 until childNodes.length)
        .asSequence()
        .map { childNodes.item(it) }
        .filterIsInstance<Element>()
        .firstOrNull { it.localNameOrNodeName() == name }

    private fun Element.children(name: String): List<Element> = (0 until childNodes.length)
        .asSequence()
        .map { childNodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.localNameOrNodeName() == name }
        .toList()

    private fun Element.findDescendant(name: String): Element? {
        child(name)?.let { return it }
        return (0 until childNodes.length)
            .asSequence()
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .mapNotNull { it.findDescendant(name) }
            .firstOrNull()
    }

    private fun Element.localNameOrNodeName(): String = localName ?: nodeName.substringAfterLast(':')

    companion object {
        const val PARSER_VERSION = "tino-fiscal-xml-v1"
    }
}
