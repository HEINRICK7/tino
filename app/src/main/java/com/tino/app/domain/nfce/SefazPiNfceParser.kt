package com.tino.app.domain.nfce

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Converts only the observed PI result layout into the HTML-free purchase contract. */
class SefazPiNfceParser {
    fun parse(html: String, fallbackAccessKey: String? = null): PurchaseDocument {
        require(html.contains("tbLeiauteDANFENFCe", ignoreCase = true)) {
            "Resultado da NFC-e do Piauí não encontrado."
        }
        val text = cleanText(html)
        val rows = ITEM_ROW.findAll(html).toList()
        require(rows.isNotEmpty()) { "O resultado não contém produtos." 
        }
        val items = rows.mapIndexed { index, match ->
            val cells = TD.findAll(match.groupValues[2]).map { cleanText(it.groupValues[1]) }.toList()
            require(cells.size >= 6) { "Linha de produto sem seis células." 
            }
            PurchaseItem(
                lineNumber = match.groupValues[1].toIntOrNull() ?: index + 1,
                externalCode = cells[0].ifBlank { null },
                gtin = null,
                description = cells[1],
                quantity = cells[2].toBrazilianDecimalOrNull(),
                unit = cells[3].ifBlank { null },
                unitPrice = cells[4].toBrazilianDecimalOrNull(),
                totalPrice = cells[5].toBrazilianDecimalOrNull(),
            )
        }
        val accessKey = ACCESS_KEY.find(text)?.groupValues?.get(1)?.filter(Char::isDigit)
            ?: fallbackAccessKey
            ?: error("Chave de acesso não encontrada no resultado da SEFAZ-PI.")
        val context = NfceAccessKey.normalizeAndValidate(accessKey)
        val issuerCells = ISSUER_CELL.findAll(html).map { cleanText(it.groupValues[1]) }.toList()
        val name = issuerCells.firstOrNull { it.isNotBlank() && !it.contains("CNPJ:", true) && !it.contains("Inscrição Estadual:", true) }
        val taxId = CNPJ.find(text)?.groupValues?.get(1)
        val issuedAt = DATE.find(text)?.groupValues?.get(1)?.let(::parseDate)
        val total = TOTAL.find(text)?.groupValues?.get(1)?.toBrazilianDecimalOrNull()
        return PurchaseDocument(
            source = PurchaseDocument.Source.NFCE,
            documentType = PurchaseDocument.DocumentType.NFCE,
            accessKey = context.accessKey,
            issuedAt = issuedAt,
            issuer = PurchaseIssuer(name = name, taxId = taxId),
            items = items,
            total = total,
        )
    }

    private fun parseDate(value: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(value, DATE_FORMATTER)
    }.getOrNull()

    private fun cleanText(value: String): String = decodeEntities(value.replace(TAG, " "))
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun decodeEntities(value: String): String = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'")

    private fun String.toBrazilianDecimalOrNull(): BigDecimal? {
        if (isBlank()) return null
        val normalized = replace(Regex("[^0-9,.-]"), "")
            .replace(".", "")
            .replace(',', '.')
        return normalized.toBigDecimalOrNull()
    }

    private companion object {
        val ITEM_ROW = Regex("<tr\\s+id=\\\"Item\\s*\\+\\s*(\\d+)\\\"[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
        val TD = Regex("<td\\b[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)
        val TAG = Regex("<[^>]*>")
        val ISSUER_CELL = Regex("<td\\s+class=\\\"NFCCabecalho_SubTitulo1\\\"[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)
        val ACCESS_KEY = Regex("CHAVE DE ACESSO\\s+((?:\\d\\s*){44})(?!\\d)", RegexOption.IGNORE_CASE)
        val CNPJ = Regex("CNPJ:\\s*([\\d./-]+)", RegexOption.IGNORE_CASE)
        val DATE = Regex("Data de Emissão:\\s*(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})", RegexOption.IGNORE_CASE)
        val TOTAL = Regex("Valor Total R\\$\\s+([\\d.,]+)", RegexOption.IGNORE_CASE)
        val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ROOT)
    }
}
