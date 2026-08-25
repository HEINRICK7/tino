package com.tino.app.interfaceadapter.a2ui

/** Versioned wire codec for the declarative surface lifecycle. */
object TinoA2UiSurfaceJsonCodec {
    private const val MAX_PAYLOAD_LENGTH = 64 * 1024

    fun encode(message: A2uiSurfaceMessage): String = buildString {
        append("{\"schema\":")
        appendJsonString(message.schema)
        append(",\"version\":")
        append(message.version)
        append(",\"messageId\":")
        appendJsonString(message.messageId)
        append(",\"surfaceId\":")
        appendJsonString(message.surfaceId)
        append(",\"operation\":")
        appendJsonString(message.operation.name)
        append(",\"isFinal\":")
        append(message.isFinal)
        append(",\"components\":[")
        message.components.forEachIndexed { index, component ->
            if (index > 0) append(',')
            append("{\"componentId\":")
            appendJsonString(component.componentId)
            append(",\"type\":")
            appendJsonString(component.type)
            append(",\"props\":")
            appendStringMap(component.props)
            append(",\"bindings\":")
            appendStringMap(component.bindings)
            append(",\"actions\":[")
            component.actions.forEachIndexed { actionIndex, action ->
                if (actionIndex > 0) append(',')
                appendJsonString(action)
            }
            append("],\"actionLabels\":")
            appendStringMap(component.actionLabels)
            append(",\"actionPayloads\":")
            appendNestedStringMap(component.actionPayloads)
            append('}')
        }
        append("],\"dataModel\":")
        appendStringMap(message.dataModel)
        append('}')
    }

    fun decode(json: String): A2uiSurfaceMessage {
        return runCatching {
            require(json.length <= MAX_PAYLOAD_LENGTH) { "payload too large" }
            @Suppress("UNCHECKED_CAST")
            val root = TinoA2UiJsonCodec.JsonParser(json).parseValue() as? Map<String, Any?>
                ?: error("root is not an object")
            @Suppress("UNCHECKED_CAST")
            val componentObjects = root["components"] as? List<Map<String, Any?>> ?: emptyList()
            A2uiSurfaceMessage(
                messageId = root.string("messageId") ?: "unknown",
                surfaceId = root.string("surfaceId") ?: "",
                operation = root.string("operation")?.let { value ->
                    runCatching { A2uiSurfaceOperation.valueOf(value) }.getOrDefault(A2uiSurfaceOperation.UPDATE_DATA_MODEL)
                } ?: A2uiSurfaceOperation.UPDATE_DATA_MODEL,
                isFinal = root.boolean("isFinal") ?: false,
                components = componentObjects.map { component ->
                    A2uiSurfaceComponent(
                        componentId = component.string("componentId").orEmpty(),
                        type = component.string("type").orEmpty(),
                        props = component.stringMap("props"),
                        bindings = component.stringMap("bindings"),
                        actions = component.stringList("actions"),
                        actionLabels = component.stringMap("actionLabels"),
                        actionPayloads = component.nestedStringMap("actionPayloads"),
                    )
                },
                dataModel = root.stringMap("dataModel"),
                schema = root.string("schema") ?: "unknown",
                version = root.number("version")?.toInt() ?: -1,
            )
        }.getOrElse { error ->
            A2uiSurfaceMessage(
                messageId = "invalid",
                surfaceId = "",
                operation = A2uiSurfaceOperation.UPDATE_DATA_MODEL,
                schema = "invalid",
                version = -1,
                components = listOf(
                    A2uiSurfaceComponent(
                        componentId = "invalid",
                        type = "invalid",
                        props = mapOf("reason" to (error.message ?: "formato desconhecido")),
                    ),
                ),
            )
        }
    }

    private fun StringBuilder.appendStringMap(values: Map<String, String>) {
        append('{')
        values.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            appendJsonString(entry.key)
            append(':')
            appendJsonString(entry.value)
        }
        append('}')
    }

    private fun StringBuilder.appendNestedStringMap(values: Map<String, Map<String, String>>) {
        append('{')
        values.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            appendJsonString(entry.key)
            append(':')
            appendStringMap(entry.value)
        }
        append('}')
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.number(key: String): Number? = this[key] as? Number
    private fun Map<String, Any?>.boolean(key: String): Boolean? = this[key] as? Boolean

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.stringMap(key: String): Map<String, String> =
        (this[key] as? Map<String, Any?>).orEmpty().mapNotNull { (name, value) ->
            (value as? String)?.let { name to it }
        }.toMap()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (this[key] as? List<Any?>).orEmpty().mapNotNull { it as? String }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.nestedStringMap(key: String): Map<String, Map<String, String>> =
        (this[key] as? Map<String, Any?>).orEmpty().mapNotNull { (name, value) ->
            val nested = (value as? Map<String, Any?>).orEmpty().mapNotNull { (nestedName, nestedValue) ->
                (nestedValue as? String)?.let { nestedName to it }
            }.toMap()
            name to nested
        }.toMap()
}
