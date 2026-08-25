package com.tino.app.domain.intelligence

import java.text.Normalizer
import java.util.Locale

object IntelligenceTextNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}".toRegex(), "")
        .lowercase(Locale.ROOT)
}

