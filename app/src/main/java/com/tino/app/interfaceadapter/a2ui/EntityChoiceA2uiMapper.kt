package com.tino.app.interfaceadapter.a2ui

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts safe local clarification options into a declarative choice surface.
 * Options are labels, never model-supplied IDs or financial values.
 */
@Singleton
class EntityChoiceA2uiMapper @Inject constructor() {
    fun map(
        entityType: String,
        options: List<String>,
    ): A2uiMessage {
        val safeOptions = options.map(String::trim).filter(String::isNotBlank).distinct()
        return A2uiMessage(
            messageId = UUID.randomUUID().toString(),
            component = A2uiComponent.EntityChoice(
                title = if (entityType == "payment_method") "Como recebeu?" else "Qual ${entityLabel(entityType)}?",
                entityType = entityType,
                prompt = if (entityType == "payment_method") {
                    "Escolha uma opção."
                } else {
                    "Escolha uma opção."
                },
                options = safeOptions.map(::A2uiChoiceOption),
            ),
        )
    }

    private fun entityLabel(entityType: String): String = when (entityType) {
        "customer" -> "cliente"
        "product" -> "produto"
        "supplier" -> "fornecedor"
        else -> "opção"
    }
}
