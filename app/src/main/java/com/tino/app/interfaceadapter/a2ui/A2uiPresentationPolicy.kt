package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoCapabilityRegistry
import com.tino.app.domain.agent.TinoPresentationMode
import com.tino.app.domain.language.TinoIntent

/** Presentation decisions stay at the interface boundary and never execute domain work. */
object A2uiPresentationPolicy {
    fun forCapability(capability: TinoCapabilityId): TinoPresentationMode =
        TinoCapabilityRegistry.require(capability).presentation

    fun forComponent(componentType: String, itemCount: Int = 0): TinoPresentationMode = when {
        componentType in setOf(
            TinoA2UiComponentCatalog.PRODUCT_LIST,
            TinoA2UiComponentCatalog.CUSTOMER_LIST,
            TinoA2UiComponentCatalog.RECEIVABLES_LIST,
            TinoA2UiComponentCatalog.OVERDUE_LIST,
        ) && itemCount > 1 -> TinoPresentationMode.BOTTOM_SHEET
        componentType == TinoA2UiComponentCatalog.ENTITY_CHOICE && itemCount > 3 ->
            TinoPresentationMode.BOTTOM_SHEET
        else -> TinoPresentationMode.OVERLAY
    }

    fun forIntent(intent: TinoIntent): TinoPresentationMode = when (intent) {
        TinoIntent.CORRECTION,
        TinoIntent.NEGATION,
        -> TinoPresentationMode.OVERLAY
        TinoIntent.RECEIVE_CREDIT_PAYMENT,
        TinoIntent.ADD_CREDIT,
        TinoIntent.ADD_CREDIT_ITEM,
        TinoIntent.REGISTER_STOCK_ENTRY,
        TinoIntent.CHANGE_PRICE,
        -> TinoPresentationMode.BOTTOM_SHEET
        else -> TinoPresentationMode.OVERLAY
    }

    fun semanticComponentFor(intent: TinoIntent): String =
        A2uiSemanticComponentRegistry.forIntent(intent)

    fun isSafeComponent(type: String): Boolean = type in TinoA2UiComponentCatalog.allowlist
}
