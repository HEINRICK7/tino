package com.tino.app.domain.nfce

import org.junit.Assert.assertEquals
import org.junit.Test

class NfceAccessKeyTest {
    private val validPiNfce = "22260831838128000748650120002104021782591975"

    @Test
    fun acceptsPiModel65AndQrCandidate() {
        val context = NfceAccessKey.normalizeAndValidate(validPiNfce)

        assertEquals(NfceAccessKey.PIAUI_UF, context.ufCode)
        assertEquals(NfceAccessKey.NFCE_MODEL, context.model)
        assertEquals(validPiNfce, NfceQrAccessKeyExtractor.extract("https://sefaz.pi.gov.br/qrcode=$validPiNfce"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidLength() {
        NfceAccessKey.normalizeAndValidate("22")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherModel() {
        NfceAccessKey.normalizeAndValidate(validPiNfce.replaceRange(20, 22, "55"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCheckDigit() {
        NfceAccessKey.normalizeAndValidate(validPiNfce.dropLast(1) + "0")
    }
}
