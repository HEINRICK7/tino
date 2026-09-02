package com.tino.app.interfaceadapter.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TinoComponentCatalogTest {
    @Test
    fun coreCatalogCoversEverySemanticGroup() {
        val groups = TinoComponentCatalog.core.descriptors.map { it.group }.toSet()

        assertEquals(TinoComponentGroup.values().toSet(), groups)
        assertTrue(TinoComponentCatalog.core.types.contains(CoreTinoComponentCatalog.MONEY))
        assertTrue(TinoComponentCatalog.core.types.contains(CoreTinoComponentCatalog.CUSTOMER_BALANCE))
        assertTrue(TinoComponentCatalog.core.types.contains(CoreTinoComponentCatalog.COMPARISON))
        assertTrue(TinoComponentCatalog.core.types.contains(CoreTinoComponentCatalog.CONFIRMATION))
        assertEquals("tino.catalog.v1", TinoCatalogVersion.ID)
        assertTrue(TinoComponentCatalog.core.types.contains(TinoCustomComponentCatalog.PRODUCT_CARD))
        assertTrue(TinoComponentCatalog.core.types.contains(TinoCustomComponentCatalog.CATALOG_CARD))
        assertTrue(TinoComponentCatalog.core.types.contains(TinoCustomComponentCatalog.CATALOG_LIST_CARD))
        assertTrue(TinoComponentCatalog.core.types.contains(TinoCustomComponentCatalog.QUICK_QUERY_CARD))
        assertTrue(TinoComponentCatalog.core.types.contains(TinoCustomComponentCatalog.ACTION_LIST_CARD))
        assertTrue(TinoComponentCatalog.core.types.contains(TinoCustomComponentCatalog.TIMELINE_CARD))
        assertTrue(TinoComponentCatalog.core.types.contains(TinoCustomComponentCatalog.EMPTY_STATE_CARD))
    }

    @Test
    fun descriptorsDeclarePropSchemaAndRequiredBindingsAreAccepted() {
        val component = A2uiSurfaceComponent(
            componentId = "metric",
            type = CoreTinoComponentCatalog.MONEY,
            props = mapOf("label" to "Recebido"),
            bindings = mapOf("value" to "received"),
        )

        val descriptor = TinoComponentCatalog.core.descriptor(CoreTinoComponentCatalog.MONEY)!!

        assertEquals(setOf("label", "value"), descriptor.propNames)
        assertEquals(TinoComponentValidation.Allowed, TinoComponentCatalogValidator.validate(component))
    }

    @Test
    fun customCatalogDeclaresDomainPropsAndSafeActions() {
        val descriptor = TinoComponentCatalog.core.descriptor(TinoCustomComponentCatalog.PRODUCT_CARD)!!

        assertEquals(
            setOf("icon", "title", "context", "value", "supportingText", "status"),
            descriptor.propNames,
        )
        assertEquals(setOf("title", "value"), descriptor.requiredProps)
        assertEquals(setOf("request_details"), descriptor.actions.map { it.name }.toSet())
    }

    @Test
    fun customConfirmationUsesOnlyExplicitMutationActions() {
        val descriptor = TinoComponentCatalog.core.descriptor(TinoCustomComponentCatalog.CONFIRMATION_CARD)!!

        assertEquals(
            setOf("confirm_operation", "cancel_operation"),
            descriptor.actions.map { it.name }.toSet(),
        )
        assertTrue(descriptor.actions.first { it.name == "confirm_operation" }.requiredPayloadKeys.isNotEmpty())
    }

    @Test
    fun unknownTypeIsInertFallbackNotAnExecutableInstruction() {
        val validation = TinoComponentCatalogValidator.validate(
            A2uiSurfaceComponent("unsafe", "execute_arbitrary_code", props = mapOf("script" to "rm")),
        )

        assertTrue(validation is TinoComponentValidation.Unknown)
        assertFalse(TinoComponentCatalog.core.types.contains("execute_arbitrary_code"))
    }

    @Test
    fun knownTypeWithUndeclaredPropsIsRejected() {
        val validation = TinoComponentCatalogValidator.validate(
            A2uiSurfaceComponent(
                componentId = "metric",
                type = CoreTinoComponentCatalog.MONEY,
                props = mapOf("label" to "Recebido", "onClick" to "delete-all"),
                bindings = mapOf("value" to "received"),
            ),
        )

        assertTrue(validation is TinoComponentValidation.InvalidProps)
    }

    @Test
    fun verticalContributorExtendsEffectiveCatalogWithoutChangingCore() {
        val bakery = object : TinoComponentCatalogContributor {
            override fun components() = listOf(
                TinoComponentDescriptor(
                    type = "bakery.order_summary",
                    group = TinoComponentGroup.BUSINESS,
                ),
            )
        }
        val effective = TinoComponentCatalog.effective(listOf(bakery))

        assertTrue(effective.types.contains("bakery.order_summary"))
        assertTrue(effective.types.contains(CoreTinoComponentCatalog.INSIGHT))
        assertFalse(TinoComponentCatalog.core.types.contains("bakery.order_summary"))
        assertTrue(effective.duplicateTypes.isEmpty())
    }

    @Test
    fun duplicateVerticalTypeIsObservable() {
        val first = object : TinoComponentCatalogContributor {
            override fun components() = listOf(TinoComponentDescriptor("vertical.card", TinoComponentGroup.BUSINESS))
        }
        val second = object : TinoComponentCatalogContributor {
            override fun components() = listOf(TinoComponentDescriptor("vertical.card", TinoComponentGroup.INTELLIGENCE))
        }

        assertEquals(setOf("vertical.card"), TinoComponentCatalog.effective(listOf(first, second)).duplicateTypes)
    }

    @Test
    fun surfaceHostRejectsInvalidKnownPropsButKeepsUnknownForFallback() {
        val host = A2uiSurfaceHost()
        val invalid = host.apply(
            A2uiSurfaceMessage(
                messageId = "invalid",
                surfaceId = "surface",
                operation = A2uiSurfaceOperation.CREATE_SURFACE,
                components = listOf(
                    A2uiSurfaceComponent(
                        componentId = "metric",
                        type = CoreTinoComponentCatalog.MONEY,
                        props = mapOf("onClick" to "delete-all"),
                    ),
                ),
            ),
        )
        val unknown = host.apply(
            A2uiSurfaceMessage(
                messageId = "unknown",
                surfaceId = "fallback",
                operation = A2uiSurfaceOperation.CREATE_SURFACE,
                components = listOf(A2uiSurfaceComponent("custom", "future.component")),
            ),
        )

        assertTrue(invalid is A2uiSurfaceApplyResult.Rejected)
        assertTrue(unknown is A2uiSurfaceApplyResult.Applied)
        assertTrue(host.snapshot("fallback")!!.components.single().type == "future.component")
    }
}
