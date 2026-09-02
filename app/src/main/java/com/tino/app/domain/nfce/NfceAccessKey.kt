package com.tino.app.domain.nfce

/** Validates the PI NFC-e access-key identity before the SEFAZ flow starts. */
data class NfceContext(
    val accessKey: String,
    val ufCode: String,
    val model: String,
)

object NfceAccessKey {
    const val PIAUI_UF = "22"
    const val NFCE_MODEL = "65"
    private const val LENGTH = 44

    fun normalizeAndValidate(input: String): NfceContext {
        val key = input.filterNot(Char::isWhitespace)
        require(key.length == LENGTH && key.all(Char::isDigit)) {
            "A chave da NFC-e deve conter 44 dígitos."
        }
        val ufCode = key.substring(0, 2)
        val model = key.substring(20, 22)
        require(ufCode == PIAUI_UF) { "Esta NFC-e não pertence ao Piauí." }
        require(model == NFCE_MODEL) { "A chave informada não é de uma NFC-e modelo 65." }
        require(hasValidCheckDigit(key)) { "A chave da NFC-e possui dígito verificador inválido." }
        return NfceContext(key, ufCode, model)
    }

    private fun hasValidCheckDigit(key: String): Boolean {
        val body = key.dropLast(1)
        var weight = 2
        var sum = 0
        for (digit in body.reversed()) {
            sum += digit.digitToInt() * weight
            weight = if (weight == 9) 2 else weight + 1
        }
        val remainder = sum % 11
        val checkDigit = if (remainder == 0 || remainder == 1) 0 else 11 - remainder
        return checkDigit == key.last().digitToInt()
    }
}
