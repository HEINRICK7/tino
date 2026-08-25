package com.tino.app.domain.language

import com.tino.app.domain.commerce.PaymentMethod
import java.text.Normalizer

object LanguageNormalizer {
    fun normalize(value: String): String = Normalizer
        .normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("[^a-z0-9?,.]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

object DomainLexicon {
    val financial = setOf(
        "dinheiro", "pix", "cartao", "debito", "credito", "maquininha",
        "troco", "caixa", "recebido", "receber", "pagar", "pago",
    )
    val credit = setOf(
        "fiado", "caderneta", "conta", "divida", "devendo", "dever", "acertar", "pagamento",
    )
    val inventory = setOf(
        "estoque", "mercadoria", "produto", "entrada", "saida", "acabou", "acabando", "sobrando", "chegou",
    )
    val suppliers = setOf("fornecedor", "distribuidora", "representante", "pedido", "compra")
}

object IntentLexicon {
    val examples: Map<TinoIntent, Set<String>> = mapOf(
        TinoIntent.ADD_CREDIT to setOf(
            "bota na conta", "colocar na conta", "anotar no fiado", "levou fiado", "ficou devendo",
            "deixa na conta", "anota pra ele", "anota pra ela",
        ),
        TinoIntent.RECEIVE_CREDIT_PAYMENT to setOf(
            "pagou a conta", "pagou o fiado", "acertou a conta", "veio pagar", "deu uma parte", "baixou a divida",
        ),
        TinoIntent.REGISTER_STOCK_ENTRY to setOf(
            "chegou mercadoria", "chegou produto", "deu entrada", "entrou mercadoria", "chegou uma caixa", "recebi mercadoria",
        ),
        TinoIntent.READ_RECEIVABLES to setOf(
            "quem esta me devendo", "quem deve", "quanto tenho para receber", "quanto tem no fiado",
        ),
        TinoIntent.READ_FINANCIAL_SUMMARY to setOf(
            "quanto entrou hoje", "quanto recebi hoje", "quanto vendeu hoje", "quanto vendi hoje",
        ),
    )
}

enum class CommercialUnit {
    UNIT,
    BOX,
    PACKAGE,
    BUNDLE,
    DOZEN,
    KILOGRAM,
    GRAM,
    LITER,
}

object UnitLexicon {
    private val aliases: Map<CommercialUnit, Set<String>> = mapOf(
        CommercialUnit.UNIT to setOf("unidade", "unidades", "un", "und"),
        CommercialUnit.BOX to setOf("caixa", "caixas", "cx"),
        CommercialUnit.PACKAGE to setOf("pacote", "pacotes", "pct"),
        CommercialUnit.BUNDLE to setOf("fardo", "fardos"),
        CommercialUnit.DOZEN to setOf("duzia"),
        CommercialUnit.KILOGRAM to setOf("quilo", "kg", "quilograma", "quilogramas"),
        CommercialUnit.GRAM to setOf("grama", "gramas", "g"),
        CommercialUnit.LITER to setOf("litro", "litros", "l"),
    )

    private val reverseAliases = aliases.flatMap { (unit, words) -> words.map { it to unit } }.toMap()

    fun resolve(value: String): CommercialUnit? = reverseAliases[LanguageNormalizer.normalize(value)]

    fun aliasesFor(unit: CommercialUnit): Set<String> = aliases.getValue(unit)
}

object PaymentMethodLexicon {
    private val aliases = mapOf(
        PaymentMethod.CASH to setOf("dinheiro", "em dinheiro", "especie", "em especie"),
        PaymentMethod.PIX to setOf("pix", "no pix", "pelo pix", "via pix"),
        PaymentMethod.CARD to setOf("cartao", "no cartao", "maquininha"),
    )

    fun resolve(value: String): PaymentMethod? {
        val normalized = LanguageNormalizer.normalize(value)
        return aliases.entries.firstOrNull { normalized in it.value }?.key
    }

    fun aliasesFor(method: PaymentMethod): Set<String> = aliases[method].orEmpty()
}
