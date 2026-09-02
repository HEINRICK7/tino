package com.tino.app.domain.nfce

/** Extracts a candidate only; semantic validation belongs to NfceAccessKey. */
object NfceQrAccessKeyExtractor {
    private val accessKey = Regex("(?<!\\d)(\\d{44})(?!\\d)")

    fun extract(rawQrContent: String): String? = accessKey.findAll(rawQrContent)
        .map { it.groupValues[1] }
        .firstOrNull { it.startsWith(NfceAccessKey.PIAUI_UF) }
        ?: accessKey.find(rawQrContent)?.groupValues?.get(1)
}
