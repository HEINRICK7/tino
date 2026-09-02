package com.tino.app.domain.language

import com.tino.app.domain.commerce.PaymentMethod
import javax.inject.Inject

class DeterministicLanguageInterpreter @Inject constructor() : LanguageIntentInterpreter {
    override suspend fun interpret(input: LanguageInput): IntentInterpretation? {
        val text = LanguageNormalizer.normalize(input.transcript)
        if (text.isBlank()) return null
        fun base(
            intent: TinoIntent,
            refs: List<EntityReference> = emptyList(),
            quantity: ParsedQuantity? = null,
            amount: Long? = null,
            unitCost: Long? = null,
            method: PaymentMethod? = null,
            operations: List<IntentInterpretation> = emptyList(),
        ): IntentInterpretation =
            IntentInterpretation(
                intent = intent,
                references = refs,
                quantity = quantity,
                amountCents = amount,
                unitCostCents = unitCost,
                paymentMethod = method,
                source = input.source,
                transcript = input.transcript,
                subintent = when (intent) {
                    TinoIntent.ADD_CREDIT, TinoIntent.ADD_CREDIT_ITEM -> CommerceSubintent.ADD_ITEM
                    TinoIntent.RECEIVE_CREDIT_PAYMENT -> CommerceSubintent.REGISTER_RECEIPT
                    TinoIntent.REGISTER_STOCK_ENTRY -> CommerceSubintent.REGISTER_STOCK
                    TinoIntent.READ_CUSTOMER_BALANCE,
                    TinoIntent.READ_CUSTOMER_TIMELINE,
                    -> CommerceSubintent.READ_BALANCE
                    else -> null
                },
                operations = operations,
            )

        if (isFinancialSummary(text)) return base(TinoIntent.READ_FINANCIAL_SUMMARY)
        if (isReceivables(text)) return base(TinoIntent.READ_RECEIVABLES)

        parseCompound(text)?.let { compound ->
            return base(
                intent = TinoIntent.COMPOUND,
                refs = compound.operations.flatMap { it.references }.distinct(),
                operations = compound.operations,
            )
        }

        parseCreditPayment(text)?.let { payment ->
            return base(
                intent = TinoIntent.RECEIVE_CREDIT_PAYMENT,
                refs = listOf(EntityReference(LanguageEntityType.CUSTOMER, payment.customer)),
                amount = payment.amountCents,
                method = payment.paymentMethod,
            )
        }

        parseCreditItem(text)?.let { credit ->
            return base(
                intent = if (credit.product == null) TinoIntent.ADD_CREDIT else TinoIntent.ADD_CREDIT_ITEM,
                refs = buildList {
                    add(EntityReference(LanguageEntityType.CUSTOMER, credit.customer))
                    credit.product?.let { add(EntityReference(LanguageEntityType.PRODUCT, it)) }
                },
                quantity = credit.quantity,
            )
        }

        parseStockEntry(text)?.let { stock ->
            return base(
                intent = TinoIntent.REGISTER_STOCK_ENTRY,
                refs = listOf(EntityReference(LanguageEntityType.PRODUCT, stock.product)),
                quantity = stock.quantity,
                unitCost = stock.unitCostCents,
            )
        }

        if (
            text.contains("tem ") ||
            text.contains("quanto tem") ||
            text.contains("quanto tenho") ||
            (text.startsWith("quanto de ") && text.endsWith(" tenho")) ||
            text.contains("estoque")
        ) {
            val product = extractReference(text, listOf("tem", "quanto", "tenho", "de", "do", "da", "em", "estoque", "ainda", "dele", "dela", "esse", "essa", "aquele", "aquela"))
            if (!product.isNullOrBlank()) return base(
                TinoIntent.READ_STOCK,
                listOf(EntityReference(LanguageEntityType.PRODUCT, product)),
            )
        }

        if (text.contains("quanto") && (text.contains("deve") || text.contains("devendo"))) {
            val customer = extractReference(text, listOf("quanto", "a", "o", "da", "do", "de", "esta", "ta", "devendo", "deve"))
            if (!customer.isNullOrBlank()) return base(
                TinoIntent.READ_CUSTOMER_BALANCE,
                listOf(EntityReference(LanguageEntityType.CUSTOMER, customer)),
            )
        }

        return null
    }

    private fun isFinancialSummary(text: String): Boolean =
        (text.contains("quanto entrou") || text.contains("quanto recebi") || text.contains("quanto vendeu") || text.contains("quanto vendi")) &&
            !text.contains("quanto a ")

    private fun isReceivables(text: String): Boolean =
        text.contains("quem esta me devendo") || text.contains("quem ta me devendo") ||
            text.contains("quem me deve") || text.contains("quanto tenho para receber") ||
            text.contains("quanto tenho pra receber") || text.contains("quanto tem no fiado")

    private fun parseCreditPayment(text: String): PaymentData? {
        val customerPayment = Regex("^(.+?)(?:\\s+me)?\\s+(pagou|deu|acertou)\\s+(.+)$").matchEntire(text)
        if (customerPayment != null) {
            val amount = parseAmountAtStart(customerPayment.groupValues[3]) ?: return null
            val customer = customerPayment.groupValues[1].trim()
            if (customer.isBlank()) return null
            return PaymentData(
                customer = customer,
                amountCents = amount.first,
                paymentMethod = PaymentMethodLexicon.resolve(amount.second),
            )
        }

        if (text.startsWith("recebi ")) {
            val amount = parseAmountAtStart(text.removePrefix("recebi ")) ?: return null
            val customer = amount.third
                .removePrefix("da ")
                .removePrefix("do ")
                .removePrefix("de ")
                .trim()
            if (customer.isBlank()) return null
            return PaymentData(customer, amount.first, PaymentMethodLexicon.resolve(amount.second))
        }
        return null
    }

    private fun parseCompound(text: String): CompoundData? {
        val match = Regex("^(.+?)\\s+(pagou|deu|acertou)\\s+(.+?)\\s+(levou|pegou|ficou com)\\s+(.+)$")
            .matchEntire(text) ?: return null
        val customer = match.groupValues[1].trim()
        val payment = parseCreditPayment("$customer pagou ${match.groupValues[3]}") ?: return null
        val credit = parseCreditItem("anota ${match.groupValues[5]} pra $customer") ?: return null
        val paymentInterpretation = IntentInterpretation(
            intent = TinoIntent.RECEIVE_CREDIT_PAYMENT,
            subintent = CommerceSubintent.REGISTER_RECEIPT,
            references = listOf(EntityReference(LanguageEntityType.CUSTOMER, payment.customer)),
            amountCents = payment.amountCents,
            paymentMethod = payment.paymentMethod,
            source = LanguageSource.TEXT,
            transcript = text,
        )
        val creditInterpretation = IntentInterpretation(
            intent = if (credit.product == null) TinoIntent.ADD_CREDIT else TinoIntent.ADD_CREDIT_ITEM,
            subintent = CommerceSubintent.ADD_ITEM,
            references = buildList {
                add(EntityReference(LanguageEntityType.CUSTOMER, credit.customer))
                credit.product?.let { add(EntityReference(LanguageEntityType.PRODUCT, it)) }
            },
            quantity = credit.quantity,
            source = LanguageSource.TEXT,
            transcript = text,
        )
        return CompoundData(listOf(paymentInterpretation, creditInterpretation))
    }

    private fun parseAmountAtStart(value: String): Triple<Long, String, String>? {
        val match = Regex("^(?:r\\s*)?(\\d+(?:[.,]\\d{1,2})?|[a-z]+(?:\\s+e\\s+[a-z]+)?)(?:\\s+(?:reais|real|conto|centavos))?(.*)$").matchEntire(value)
            ?: return null
        val amountText = match.groupValues[1]
        val amount = MoneyParser.parse(amountText) ?: return null
        val remainder = match.groupValues[2].trim()
        val methodText = remainder.substringBefore(" da ").substringBefore(" do ").substringBefore(" de ").trim()
        return Triple(amount, methodText, remainder)
    }

    private fun parseCreditItem(text: String): CreditData? {
        val marker = when {
            text.contains(" na conta da ") -> " na conta da "
            text.contains(" na conta do ") -> " na conta do "
            text.contains(" na conta de ") -> " na conta de "
            text.contains(" pra ") && (
                text.startsWith("anota ") ||
                    text.startsWith("bota ") ||
                    text.startsWith("coloca ") ||
                    text.contains("fiado")
                ) -> " pra "
            else -> return null
        }
        val split = text.split(marker, limit = 2)
        if (split.size != 2) return null
        val productPart = split[0]
            .removePrefix("bota ")
            .removePrefix("anota ")
            .removePrefix("coloca ")
            .replace(Regex("\\s+fiado$"), "")
            .trim()
        val customer = split[1].removeSuffix(" fiado").trim()
        if (customer.isBlank()) return null
        val count = QuantityParser.parseCountPrefix(productPart) ?: 1
        val product = productPart
            .replace(Regex("^(?:\\d+|um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez)\\s+"), "")
            .trim()
            .takeIf { it.isNotBlank() }
        return CreditData(customer, product, ParsedQuantity(count.toBigDecimal()))
    }

    private fun parseStockEntry(text: String): StockData? {
        if (!(text.startsWith("chegou ") || text.startsWith("chegaram ") || text.startsWith("recebi mercadoria"))) return null
        val rawBody = text.removePrefix("chegaram ").removePrefix("chegou ").removePrefix("recebi mercadoria ").trim()
        val costMatch = Regex("\\s+(?:a|por|custo)\\s+(?:r\\s*)?([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:reais|real)?$").find(rawBody)
        val unitCostCents = costMatch?.groupValues?.get(1)?.let(MoneyParser::parse)
        val body = costMatch?.let { rawBody.removeRange(it.range).trim() } ?: rawBody
        if (body.startsWith("uma caixa de ")) {
            return StockData(
                product = body.removePrefix("uma caixa de ").trim(),
                quantity = QuantityParser.parse("1 caixa") ?: return null,
                unitCostCents = unitCostCents,
            )
        }
        if (body.startsWith("um pacote de ")) {
            return StockData(
                product = body.removePrefix("um pacote de ").trim(),
                quantity = QuantityParser.parse("1 pacote") ?: return null,
                unitCostCents = unitCostCents,
            )
        }
        val quantityMatch = Regex("^(.+?)\\s+(?:com|de)\\s+(.+)$").matchEntire(body)
        val product: String
        val quantity: ParsedQuantity
        if (quantityMatch != null) {
            product = quantityMatch.groupValues[1]
                .removePrefix("uma caixa de ")
                .removePrefix("um pacote de ")
                .removePrefix("uma caixa")
                .removePrefix("um pacote")
                .trim()
            quantity = QuantityParser.parse(quantityMatch.groupValues[2])
                ?: QuantityParser.parse("${QuantityParser.parseCountPrefix(quantityMatch.groupValues[2]) ?: 1} unidade")
                ?: return null
        } else {
            val packagedProduct = when {
                body.startsWith("uma caixa de ") -> body.removePrefix("uma caixa de ") to "1 caixa"
                body.startsWith("um pacote de ") -> body.removePrefix("um pacote de ") to "1 pacote"
                else -> body to "1 unidade"
            }
            product = packagedProduct.first.trim()
            quantity = QuantityParser.parse(packagedProduct.second) ?: return null
        }
        if (product.isBlank()) return null
        return StockData(product, quantity, unitCostCents)
    }

    private fun extractReference(text: String, stopWords: List<String>): String? = text
        .removeSuffix("?")
        .split(' ')
        .filter { it.isNotBlank() && it !in stopWords }
        .joinToString(" ")
        .trim()
        .takeIf { it.isNotBlank() }

    private data class PaymentData(val customer: String, val amountCents: Long, val paymentMethod: PaymentMethod?)
    private data class CreditData(val customer: String, val product: String?, val quantity: ParsedQuantity)
    private data class StockData(
        val product: String,
        val quantity: ParsedQuantity,
        val unitCostCents: Long? = null,
    )
    private data class CompoundData(val operations: List<IntentInterpretation>)
}
