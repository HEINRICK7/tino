package com.tino.app.domain.profile

import com.tino.app.domain.agent.TinoCapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessProfileTest {
    @Test
    fun retailProfileUsesOneCoreAndOneVerticalModule() {
        val profile = BusinessProfile.retail()

        assertEquals(BusinessVertical.RETAIL, profile.primaryVertical)
        assertTrue(profile.has(BusinessModule.CORE))
        assertTrue(profile.has(BusinessModule.RETAIL))
        assertEquals(listOf(TinoModuleRegistry.retail), TinoModuleRegistry.forProfile(profile))
    }

    @Test
    fun hybridProfileEnablesModulesWithoutCreatingAnotherApp() {
        val profile = BusinessProfile(
            primaryVertical = BusinessVertical.BAKERY,
            enabledModules = setOf(BusinessModule.CORE, BusinessModule.BAKERY, BusinessModule.RETAIL),
        )

        assertTrue(TinoModuleRegistry.isEnabled(profile, BusinessModule.CORE))
        assertTrue(TinoModuleRegistry.isEnabled(profile, BusinessModule.BAKERY))
        assertTrue(profile.has(BusinessModule.RETAIL))
    }

    @Test
    fun retailModuleExposesCapabilitiesToTheSharedAgentRegistry() {
        val module = TinoModuleRegistry.retail

        assertTrue(module.capabilities.contains(TinoCapabilityId.LIST_PRODUCTS))
        assertTrue(module.capabilities.contains(TinoCapabilityId.REPLENISHMENT_QUERY))
        assertTrue(module.capabilities.contains(TinoCapabilityId.LIST_RECEIVABLES))
        assertFalse(module.capabilities.contains(TinoCapabilityId.CREATE_CUSTOMER))
    }

    @Test
    fun creditModuleExposesCreditItemMutationToActiveProfiles() {
        val profile = BusinessProfile(
            primaryVertical = BusinessVertical.RETAIL,
            enabledModules = VerticalPresetCatalog.forVertical(BusinessVertical.RETAIL).defaultModules,
        )

        val capabilities = TinoModuleRegistry.capabilitiesFor(profile)

        assertTrue(capabilities.contains(TinoCapabilityId.ADD_CREDIT))
        assertTrue(capabilities.contains(TinoCapabilityId.ADD_CREDIT_ITEM))
    }

    @Test
    fun verticalPresetIsExplicitAndEnablesSharedRuntimeModules() {
        val preset = VerticalPresetCatalog.forVertical(BusinessVertical.RESTAURANT)

        assertTrue(BusinessModule.CORE in preset.defaultModules)
        assertTrue(BusinessModule.INVENTORY in preset.defaultModules)
        assertTrue(BusinessModule.CUSTOMERS in preset.defaultModules)
        assertTrue(TinoModuleRegistry.capabilitiesFor(BusinessProfile(BusinessVertical.RESTAURANT, preset.defaultModules))
            .contains(TinoCapabilityId.LIST_PRODUCTS))
    }

    @Test(expected = IllegalArgumentException::class)
    fun creditCannotBeEnabledWithoutCustomers() {
        BusinessProfile(
            primaryVertical = BusinessVertical.RETAIL,
            enabledModules = setOf(BusinessModule.CORE, BusinessModule.CREDIT),
        )
    }

    @Test
    fun homeConfigurationFollowsModulesInsteadOfVertical() {
        val profile = BusinessProfile(
            primaryVertical = BusinessVertical.OTHER,
            enabledModules = setOf(BusinessModule.CORE, BusinessModule.CUSTOMERS),
        )

        val configuration = HomeConfiguration.from(profile)

        assertTrue(configuration.has(HomeActionId.CUSTOMERS))
        assertFalse(configuration.has(HomeActionId.INVENTORY))
        assertTrue(configuration.allowedCapabilities.contains(TinoCapabilityId.LIST_CUSTOMERS))
        assertFalse(configuration.allowedCapabilities.contains(TinoCapabilityId.LIST_PRODUCTS))
    }

    @Test
    fun salesHomeActionRequiresNavigationCapabilityNotJustFinancialRead() {
        val profile = BusinessProfile(
            primaryVertical = BusinessVertical.OTHER,
            enabledModules = setOf(BusinessModule.CORE),
            permanentCapabilities = setOf(TinoCapabilityId.READ_FINANCIAL_SUMMARY),
        )

        val configuration = HomeConfiguration.from(profile)

        assertFalse(configuration.has(HomeActionId.SALES))
        assertTrue(configuration.allowedCapabilities.contains(TinoCapabilityId.READ_FINANCIAL_SUMMARY))
        assertFalse(configuration.allowedCapabilities.contains(TinoCapabilityId.NAVIGATE))
    }

    @Test
    fun contextResolverDerivesPatternsModulesAndAllowedA2uiFromProfile() {
        val profile = BusinessProfile(
            primaryVertical = BusinessVertical.OTHER,
            enabledModules = setOf(BusinessModule.CORE, BusinessModule.CUSTOMERS),
        )

        val context = DefaultBusinessContextResolver().resolve(profile)

        assertEquals(setOf(OperationalPattern.GENERAL), context.patterns)
        assertTrue(context.hasCapability(TinoCapabilityId.LIST_CUSTOMERS))
        assertFalse(context.hasCapability(TinoCapabilityId.LIST_PRODUCTS))
        assertTrue(context.allowedA2uiComponents.contains("customer_list"))
    }

    @Test
    fun ephemeralActivationIsAvailableOnlyForItsLifetimeAndDoesNotChangeProfile() {
        val profile = BusinessProfile(
            primaryVertical = BusinessVertical.OTHER,
            enabledModules = setOf(BusinessModule.CORE),
        )
        val activation = CapabilityActivation(
            capability = TinoCapabilityId.LIST_PRODUCTS,
            mode = ActivationMode.EPHEMERAL,
            grantedAtEpochMs = 100,
            expiresAtEpochMs = 200,
            source = "user_choice",
        )
        val resolver = DefaultBusinessContextResolver()

        assertTrue(resolver.resolve(profile, listOf(activation), nowEpochMs = 150)
            .hasCapability(TinoCapabilityId.LIST_PRODUCTS))
        assertFalse(resolver.resolve(profile, listOf(activation), nowEpochMs = 250)
            .hasCapability(TinoCapabilityId.LIST_PRODUCTS))
        assertFalse(profile.has(BusinessModule.INVENTORY))
    }

    @Test
    fun permanentRecoveryIsLimitedToQueriesAndNavigation() {
        assertTrue(CapabilityRecoveryPolicy.canActivatePermanently(TinoCapabilityId.LIST_PRODUCTS))
        assertFalse(CapabilityRecoveryPolicy.canActivatePermanently(TinoCapabilityId.ADD_CREDIT_ITEM))
    }

    @Test
    fun permanentCapabilityComposesWithoutModuleAndCanBeRemoved() {
        val base = BusinessProfile(
            primaryVertical = BusinessVertical.OTHER,
            enabledModules = setOf(BusinessModule.CORE, BusinessModule.CUSTOMERS),
        )
        val resolver = DefaultBusinessContextResolver()

        assertFalse(resolver.resolve(base).hasCapability(TinoCapabilityId.LIST_PRODUCTS))

        val activated = base.copy(
            permanentCapabilities = setOf(TinoCapabilityId.LIST_PRODUCTS),
        )
        assertTrue(resolver.resolve(activated).hasCapability(TinoCapabilityId.LIST_PRODUCTS))

        val removed = activated.copy(permanentCapabilities = emptySet())
        assertFalse(resolver.resolve(removed).hasCapability(TinoCapabilityId.LIST_PRODUCTS))
        assertTrue(removed.has(BusinessModule.CUSTOMERS))
        assertFalse(removed.has(BusinessModule.INVENTORY))
    }

    @Test(expected = IllegalArgumentException::class)
    fun profileRejectsPermanentMutationCapability() {
        BusinessProfile(
            primaryVertical = BusinessVertical.RETAIL,
            enabledModules = setOf(BusinessModule.CORE),
            permanentCapabilities = setOf(TinoCapabilityId.ADD_CREDIT_ITEM),
        )
    }
}
