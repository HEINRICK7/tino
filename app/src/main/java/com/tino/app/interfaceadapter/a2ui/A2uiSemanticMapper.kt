package com.tino.app.interfaceadapter.a2ui

import java.util.UUID

/** Maps semantic runtime outcomes to the versioned A2UI vocabulary. */
object A2uiSemanticMapper {
    fun error(
        message: String,
        title: String = "Não foi possível concluir",
    ): A2uiMessage = A2uiMessage(
        messageId = UUID.randomUUID().toString(),
        component = A2uiComponent.ErrorStatusCard(
            title = title,
            message = message,
        ),
    )
}
