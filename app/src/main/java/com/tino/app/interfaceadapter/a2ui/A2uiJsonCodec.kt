package com.tino.app.interfaceadapter.a2ui

/**
 * Small dependency-free JSON codec for the versioned A2UI envelope.
 * Unknown components remain inert data and are rendered through the fallback.
 */
object TinoA2UiJsonCodec {
    private const val MAX_PAYLOAD_LENGTH = 64 * 1024

    fun encode(message: A2uiMessage): String = buildString {
        append("{\"schema\":")
        appendJsonString(message.schema)
        append(",\"version\":")
        append(message.version)
        append(",\"messageId\":")
        appendJsonString(message.messageId)
        append(",\"component\":")
        appendComponent(message.component)
        append('}')
    }

    fun decode(json: String): A2uiMessage {
        return runCatching {
            require(json.length <= MAX_PAYLOAD_LENGTH) { "payload too large" }
            @Suppress("UNCHECKED_CAST")
            val root = JsonParser(json).parseValue() as? Map<String, Any?>
                ?: error("root is not an object")
            @Suppress("UNCHECKED_CAST")
            val componentJson = root["component"] as? Map<String, Any?>
            val component = componentJson?.let(::decodeComponent)
                ?: A2uiComponent.Unsupported("missing", "O componente A2UI não foi informado.")
            A2uiMessage(
                messageId = root.string("messageId") ?: "unknown",
                component = component,
                schema = root.string("schema") ?: "unknown",
                version = root.number("version")?.toInt() ?: -1,
            )
        }.getOrElse { error ->
            A2uiMessage(
                messageId = "invalid",
                component = A2uiComponent.Unsupported(
                    type = "invalid",
                    reason = "Mensagem A2UI inválida: ${error.message ?: "formato desconhecido"}.",
                ),
                schema = "invalid",
                version = -1,
            )
        }
    }

    private fun StringBuilder.appendComponent(component: A2uiComponent) {
        when (component) {
            is A2uiComponent.FinancialSummaryCard -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"title\":")
                appendJsonString(component.title)
                append(",\"primaryLabel\":")
                appendJsonString(component.primaryLabel)
                append(",\"primaryValueText\":")
                appendJsonString(component.primaryValueText)
                append(",\"metrics\":[")
                component.metrics.forEachIndexed { index, metric ->
                    if (index > 0) append(',')
                    append("{\"key\":")
                    appendJsonString(metric.key)
                    append(",\"label\":")
                    appendJsonString(metric.label)
                    append(",\"valueText\":")
                    appendJsonString(metric.valueText)
                    append('}')
                }
                append("],\"emptyMessage\":")
                if (component.emptyMessage == null) {
                    append("null")
                } else {
                    appendJsonString(component.emptyMessage)
                }
                append(",\"dataSource\":")
                appendJsonString(component.dataSource)
                append('}')
            }

            is A2uiComponent.Unsupported -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"reason\":")
                appendJsonString(component.reason)
                append('}')
            }

            is A2uiComponent.ErrorStatusCard -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"title\":")
                appendJsonString(component.title)
                append(",\"message\":")
                appendJsonString(component.message)
                append(",\"retryLabel\":")
                appendJsonString(component.retryLabel)
                append('}')
            }

            is A2uiComponent.InsightCard -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"title\":")
                appendJsonString(component.title)
                append(",\"answer\":")
                appendJsonString(component.answer)
                append(",\"status\":")
                appendJsonString(component.status)
                append(",\"evidence\":[")
                component.evidence.forEachIndexed { index, row ->
                    if (index > 0) append(',')
                    append("{\"label\":")
                    appendJsonString(row.label)
                    append(",\"value\":")
                    appendJsonString(row.value)
                    append('}')
                }
                append("],\"limitations\":[")
                component.limitations.forEachIndexed { index, limitation ->
                    if (index > 0) append(',')
                    appendJsonString(limitation)
                }
                append("],\"dataSource\":")
                appendJsonString(component.dataSource)
                append('}')
            }

            is A2uiComponent.EntityChoice -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"title\":")
                appendJsonString(component.title)
                append(",\"entityType\":")
                appendJsonString(component.entityType)
                append(",\"prompt\":")
                appendJsonString(component.prompt)
                append(",\"options\":[")
                component.options.forEachIndexed { index, option ->
                    if (index > 0) append(',')
                    append("{\"label\":")
                    appendJsonString(option.label)
                    append('}')
                }
                append("]}")
            }

            is A2uiComponent.ActionConfirmation -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"title\":")
                appendJsonString(component.title)
                append(",\"detail\":")
                appendJsonString(component.detail)
                append(",\"confirmLabel\":")
                appendJsonString(component.confirmLabel)
                append(",\"complete\":")
                append(component.complete)
                append(",\"semanticType\":")
                appendJsonString(component.semanticType)
                component.operationId?.let {
                    append(",\"operationId\":")
                    appendJsonString(it)
                }
                component.activityId?.let {
                    append(",\"activityId\":")
                    appendJsonString(it)
                }
                append(",\"undoAvailable\":")
                append(component.undoAvailable)
                component.entityName?.let {
                    append(",\"entityName\":")
                    appendJsonString(it)
                }
                component.primaryValueText?.let {
                    append(",\"primaryValueText\":")
                    appendJsonString(it)
                }
                append(",\"detailRows\":[")
                component.detailRows.forEachIndexed { index, row ->
                    if (index > 0) append(',')
                    append("{\"label\":")
                    appendJsonString(row.label)
                    append(",\"value\":")
                    appendJsonString(row.value)
                    append('}')
                }
                append(']')
                component.iconKey?.let {
                    append(",\"iconKey\":")
                    appendJsonString(it)
                }
                append('}')
            }

            is A2uiComponent.CustomerBalanceCard -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"title\":")
                appendJsonString(component.title)
                append(",\"customerName\":")
                appendJsonString(component.customerName)
                append(",\"currentBalanceText\":")
                appendJsonString(component.currentBalanceText)
                append(",\"openText\":")
                appendJsonString(component.openText)
                append(",\"overdueText\":")
                appendJsonString(component.overdueText)
                component.oldestOpenText?.let {
                    append(",\"oldestOpenText\":")
                    appendJsonString(it)
                }
                component.emptyMessage?.let {
                    append(",\"emptyMessage\":")
                    appendJsonString(it)
                }
                append(",\"dataSource\":")
                appendJsonString(component.dataSource)
                append('}')
            }

            is A2uiComponent.CustomerTimelineCard -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"title\":")
                appendJsonString(component.title)
                append(",\"customerName\":")
                appendJsonString(component.customerName)
                append(",\"currentBalanceText\":")
                appendJsonString(component.currentBalanceText)
                append(",\"items\":[")
                component.items.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    append("{\"dateText\":")
                    appendJsonString(item.dateText)
                    append(",\"label\":")
                    appendJsonString(item.label)
                    append(",\"amountText\":")
                    appendJsonString(item.amountText)
                    append('}')
                }
                append("],\"emptyMessage\":")
                component.emptyMessage?.let { appendJsonString(it) } ?: append("null")
                append(",\"dataSource\":")
                appendJsonString(component.dataSource)
                append('}')
            }

            is A2uiComponent.ReadListCard -> {
                append("{\"type\":")
                appendJsonString(component.type)
                append(",\"title\":")
                appendJsonString(component.title)
                append(",\"items\":[")
                component.items.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    append("{\"title\":")
                    appendJsonString(item.title)
                    append(",\"primaryText\":")
                    appendJsonString(item.primaryText)
                    append(",\"secondaryText\":")
                    item.secondaryText?.let { appendJsonString(it) } ?: append("null")
                    item.context?.let {
                        append(",\"context\":")
                        appendJsonString(it)
                    }
                    item.supportingText?.let {
                        append(",\"supportingText\":")
                        appendJsonString(it)
                    }
                    append(",\"status\":")
                    appendJsonString(item.status.name)
                    item.iconKey?.let {
                        append(",\"iconKey\":")
                        appendJsonString(it)
                    }
                    item.actionId?.let {
                        append(",\"actionId\":")
                        appendJsonString(it)
                    }
                    append('}')
                }
                append("],\"emptyMessage\":")
                component.emptyMessage?.let { appendJsonString(it) } ?: append("null")
                append(",\"dataSource\":")
                appendJsonString(component.dataSource)
                append('}')
            }
        }
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun decodeComponent(json: Map<String, Any?>): A2uiComponent {
        val type = json.string("type") ?: "unknown"
        if (!TinoA2UiComponentCatalog.isAllowed(type)) {
            return A2uiComponent.Unsupported(type, "Componente fora da allowlist TINO.")
        }

        return when (type) {
            TinoA2UiComponentCatalog.FINANCIAL_SUMMARY_CARD -> {
                @Suppress("UNCHECKED_CAST")
                val metricObjects = json["metrics"] as? List<Map<String, Any?>> ?: emptyList()
                A2uiComponent.FinancialSummaryCard(
                    title = json.string("title") ?: "Resumo",
                    primaryLabel = json.string("primaryLabel") ?: "Total",
                    primaryValueText = json.string("primaryValueText") ?: "R$ 0,00",
                    metrics = metricObjects.map { metric ->
                        A2uiMetric(
                            key = metric.string("key") ?: "unknown",
                            label = metric.string("label") ?: "Não identificado",
                            valueText = metric.string("valueText") ?: "R$ 0,00",
                        )
                    },
                    emptyMessage = json.string("emptyMessage")?.takeIf { it.isNotBlank() },
                    dataSource = json.string("dataSource") ?: "LOCAL_ONLY",
                )
            }

            TinoA2UiComponentCatalog.ENTITY_CHOICE -> {
                @Suppress("UNCHECKED_CAST")
                val optionObjects = json["options"] as? List<Map<String, Any?>> ?: emptyList()
                A2uiComponent.EntityChoice(
                    title = json.string("title") ?: "Escolha uma opção",
                    entityType = json.string("entityType") ?: "entity",
                    prompt = json.string("prompt") ?: "Qual você quis dizer?",
                    options = optionObjects.mapNotNull { option ->
                        option.string("label")?.takeIf { it.isNotBlank() }?.let(::A2uiChoiceOption)
                    },
                )
            }

            TinoA2UiComponentCatalog.ERROR_RECOVERY -> A2uiComponent.ErrorStatusCard(
                title = json.string("title") ?: "Não foi possível concluir",
                message = json.string("message") ?: json.string("reason")
                    ?: "Tente novamente.",
                retryLabel = json.string("retryLabel") ?: "TENTAR DE NOVO",
            )

            TinoA2UiComponentCatalog.ACTION_CONFIRMATION,
            TinoA2UiComponentCatalog.OPERATION_SUCCESS,
            TinoA2UiComponentCatalog.UNDO_ACTION,
            -> {
                @Suppress("UNCHECKED_CAST")
                val detailObjects = json["detailRows"] as? List<Map<String, Any?>> ?: emptyList()
                A2uiComponent.ActionConfirmation(
                title = json.string("title") ?: "Confirmar ação",
                detail = json.string("detail") ?: "Confira os dados antes de continuar.",
                confirmLabel = json.string("confirmLabel") ?: "CONFIRMAR",
                complete = json.boolean("complete") ?: false,
                semanticType = type,
                operationId = json.string("operationId"),
                activityId = json.string("activityId"),
                undoAvailable = json.boolean("undoAvailable") ?: false,
                entityName = json.string("entityName"),
                primaryValueText = json.string("primaryValueText"),
                detailRows = detailObjects.mapNotNull { row ->
                    val label = row.string("label") ?: return@mapNotNull null
                    val value = row.string("value") ?: return@mapNotNull null
                    A2uiDetailRow(label, value)
                },
                iconKey = json.string("iconKey"),
                )
            }

            TinoA2UiComponentCatalog.CUSTOMER_BALANCE_CARD -> A2uiComponent.CustomerBalanceCard(
                title = json.string("title") ?: "Fiado",
                customerName = json.string("customerName") ?: "Cliente",
                currentBalanceText = json.string("currentBalanceText") ?: "R$ 0,00",
                openText = json.string("openText") ?: "Em aberto: R$ 0,00",
                overdueText = json.string("overdueText") ?: "Vencido: R$ 0,00",
                oldestOpenText = json.string("oldestOpenText")?.takeIf { it.isNotBlank() },
                emptyMessage = json.string("emptyMessage")?.takeIf { it.isNotBlank() },
                dataSource = json.string("dataSource") ?: "LOCAL_ONLY",
            )

            TinoA2UiComponentCatalog.CUSTOMER_TIMELINE_CARD -> {
                @Suppress("UNCHECKED_CAST")
                val itemObjects = json["items"] as? List<Map<String, Any?>> ?: emptyList()
                A2uiComponent.CustomerTimelineCard(
                    title = json.string("title") ?: "Conta do cliente",
                    customerName = json.string("customerName") ?: "Cliente",
                    currentBalanceText = json.string("currentBalanceText") ?: "R$ 0,00",
                    items = itemObjects.map { item ->
                        A2uiTimelineItem(
                            dateText = item.string("dateText") ?: "",
                            label = item.string("label") ?: "Movimento",
                            amountText = item.string("amountText") ?: "R$ 0,00",
                        )
                    },
                    emptyMessage = json.string("emptyMessage")?.takeIf { it.isNotBlank() },
                    dataSource = json.string("dataSource") ?: "LOCAL_ONLY",
                )
            }

            TinoA2UiComponentCatalog.INSIGHT_CARD -> {
                @Suppress("UNCHECKED_CAST")
                val evidenceObjects = json["evidence"] as? List<Map<String, Any?>> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val limitations = json["limitations"] as? List<Any?> ?: emptyList()
                A2uiComponent.InsightCard(
                    title = json.string("title") ?: "Insight do TINO",
                    answer = json.string("answer") ?: "Não foi possível obter uma resposta.",
                    status = json.string("status") ?: "UNKNOWN",
                    evidence = evidenceObjects.mapNotNull { row ->
                        val label = row.string("label") ?: return@mapNotNull null
                        val value = row.string("value") ?: return@mapNotNull null
                        A2uiDetailRow(label, value)
                    },
                    limitations = limitations.mapNotNull { it as? String },
                    dataSource = json.string("dataSource") ?: "LOCAL_ONLY",
                )
            }

            TinoA2UiComponentCatalog.PRODUCT_LIST,
            TinoA2UiComponentCatalog.PRODUCT_REPLENISHMENT,
            TinoA2UiComponentCatalog.PRODUCT_STOCK,
            TinoA2UiComponentCatalog.PRODUCT_PRICE,
            TinoA2UiComponentCatalog.CUSTOMER_LIST,
            TinoA2UiComponentCatalog.RECEIVABLES_LIST,
            TinoA2UiComponentCatalog.OVERDUE_LIST,
            -> {
                @Suppress("UNCHECKED_CAST")
                val itemObjects = json["items"] as? List<Map<String, Any?>> ?: emptyList()
                A2uiComponent.ReadListCard(
                    title = json.string("title") ?: "Consulta",
                    items = itemObjects.map { item ->
                        A2uiListItem(
                            title = item.string("title") ?: "Item",
                            primaryText = item.string("primaryText") ?: "",
                            secondaryText = item.string("secondaryText"),
                            context = item.string("context"),
                            supportingText = item.string("supportingText"),
                            status = item.string("status")
                                ?.let { raw -> A2uiVisualStatus.entries.firstOrNull { it.name == raw } }
                                ?: A2uiVisualStatus.NORMAL,
                            iconKey = item.string("iconKey"),
                            actionId = item.string("actionId"),
                        )
                    },
                    emptyMessage = json.string("emptyMessage")?.takeIf { it.isNotBlank() },
                    dataSource = json.string("dataSource") ?: "LOCAL_ONLY",
                    type = type,
                )
            }

            else -> A2uiComponent.Unsupported(type, "Componente fora da allowlist TINO.")
        }
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.number(key: String): Number? = this[key] as? Number

    private fun Map<String, Any?>.boolean(key: String): Boolean? = this[key] as? Boolean

    internal class JsonParser(private val input: String) {
        private var index = 0

        fun parseValue(): Any? {
            skipWhitespace()
            val value = when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                'n' -> parseLiteral("null", null)
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                else -> parseNumber()
            }
            skipWhitespace()
            require(index == input.length) { "unexpected trailing data" }
            return value
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (consumeIf('}')) return result
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseNestedValue()
                skipWhitespace()
                if (consumeIf('}')) return result
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (consumeIf(']')) return result
            while (true) {
                result += parseNestedValue()
                skipWhitespace()
                if (consumeIf(']')) return result
                expect(',')
            }
        }

        private fun parseNestedValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                'n' -> parseLiteral("null", null)
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                else -> parseNumber()
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < input.length) {
                when (val character = input[index++]) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(index < input.length) { "unterminated escape" }
                        when (val escaped = input[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(index + 4 <= input.length) { "invalid unicode escape" }
                                val hex = input.substring(index, index + 4)
                                result.append(hex.toInt(16).toChar())
                                index += 4
                            }
                            else -> error("invalid escape")
                        }
                    }
                    else -> {
                        require(character.code >= 0x20) { "control character in string" }
                        result.append(character)
                    }
                }
            }
            error("unterminated string")
        }

        private fun parseNumber(): Number {
            val start = index
            if (peek() == '-') index++
            while (peek()?.isDigit() == true) index++
            if (peek() == '.') {
                index++
                while (peek()?.isDigit() == true) index++
            }
            require(index > start) { "expected value" }
            val raw = input.substring(start, index)
            return if (raw.contains('.')) raw.toDouble() else raw.toLong()
        }

        private fun <T> parseLiteral(expected: String, value: T): T {
            require(input.startsWith(expected, index)) { "expected $expected" }
            index += expected.length
            return value
        }

        private fun skipWhitespace() {
            while (input.getOrNull(index)?.isWhitespace() == true) index++
        }

        private fun peek(): Char? = input.getOrNull(index)

        private fun expect(expected: Char) {
            require(input.getOrNull(index) == expected) { "expected $expected" }
            index++
        }

        private fun consumeIf(expected: Char): Boolean = if (input.getOrNull(index) == expected) {
            index++
            true
        } else {
            false
        }
    }
}
