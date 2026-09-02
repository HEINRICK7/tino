package com.tino.app.interfaceadapter.a2ui

import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.language.TinoIntent

/** Semantic component names are versioned presentation vocabulary, not commands. */
object A2uiSemanticComponentRegistry {
    fun forIntent(intent: TinoIntent): String = when (intent) {
        TinoIntent.RECEIVE_CREDIT_PAYMENT -> TinoA2UiComponentCatalog.PAYMENT_PREVIEW
        TinoIntent.REGISTER_STOCK_ENTRY -> TinoA2UiComponentCatalog.STOCK_ENTRY_PREVIEW
        TinoIntent.CHANGE_PRICE -> TinoA2UiComponentCatalog.PRICE_CHANGE_PREVIEW
        TinoIntent.ADD_CREDIT,
        TinoIntent.ADD_CREDIT_ITEM,
        -> TinoA2UiComponentCatalog.CREDIT_PREVIEW
        TinoIntent.READ_STOCK -> TinoA2UiComponentCatalog.STOCK_STATUS
        TinoIntent.READ_CUSTOMER_TIMELINE -> TinoA2UiComponentCatalog.ACTION_CONFIRMATION
        TinoIntent.SEARCH_SUPPLIER -> TinoA2UiComponentCatalog.SUPPLIER_SUMMARY
        TinoIntent.CORRECTION,
        TinoIntent.NEGATION,
        -> TinoA2UiComponentCatalog.ERROR_RECOVERY
        else -> TinoA2UiComponentCatalog.ACTION_CONFIRMATION
    }

    fun forCapability(capability: TinoCapabilityId): String = when (capability) {
        TinoCapabilityId.RECEIVE_CREDIT_PAYMENT -> TinoA2UiComponentCatalog.PAYMENT_PREVIEW
        TinoCapabilityId.REGISTER_STOCK_ENTRY -> TinoA2UiComponentCatalog.STOCK_ENTRY_PREVIEW
        TinoCapabilityId.CHANGE_PRODUCT_PRICE -> TinoA2UiComponentCatalog.PRICE_CHANGE_PREVIEW
        TinoCapabilityId.ADD_CREDIT,
        TinoCapabilityId.ADD_CREDIT_ITEM,
        -> TinoA2UiComponentCatalog.CREDIT_PREVIEW
        TinoCapabilityId.READ_STOCK -> TinoA2UiComponentCatalog.STOCK_STATUS
        TinoCapabilityId.SEARCH_SUPPLIER -> TinoA2UiComponentCatalog.SUPPLIER_SUMMARY
        TinoCapabilityId.GET_CUSTOMER_CONTACT -> TinoA2UiComponentCatalog.CUSTOMER_CONTACT
        else -> TinoA2UiComponentCatalog.ACTION_CONFIRMATION
    }

    fun isAllowed(type: String): Boolean = TinoA2UiComponentCatalog.isAllowed(type)

    fun fallback(messageId: String, reason: String): A2uiMessage =
        A2uiSemanticMapper.error(
            message = reason,
            title = "Não foi possível concluir",
        ).copy(messageId = messageId)
}
