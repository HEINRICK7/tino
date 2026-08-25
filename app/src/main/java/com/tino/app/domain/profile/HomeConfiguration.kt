package com.tino.app.domain.profile

import com.tino.app.domain.agent.TinoCapabilityId

enum class HomeActionId { SALES, CREDIT, STOCK_ENTRY, INVENTORY, CUSTOMERS, FISCAL }

data class HomeConfiguration(
    val primaryActions: Set<HomeActionId>,
    val allowedCapabilities: Set<TinoCapabilityId>,
) {
    fun has(action: HomeActionId): Boolean = action in primaryActions

    companion object {
        fun from(profile: BusinessProfile): HomeConfiguration {
            return from(DefaultBusinessContextResolver().resolve(profile))
        }

        fun from(context: ResolvedBusinessContext): HomeConfiguration {
            val capabilities = context.capabilities
            val actions = buildSet {
                if (context.hasCapability(TinoCapabilityId.NAVIGATE)) add(HomeActionId.SALES)
                if (context.hasCapability(TinoCapabilityId.LIST_RECEIVABLES)) add(HomeActionId.CREDIT)
                if (context.hasCapability(TinoCapabilityId.REGISTER_STOCK_ENTRY)) add(HomeActionId.STOCK_ENTRY)
                if (context.hasCapability(TinoCapabilityId.LIST_PRODUCTS)) add(HomeActionId.INVENTORY)
                if (context.hasCapability(TinoCapabilityId.LIST_CUSTOMERS)) add(HomeActionId.CUSTOMERS)
                if (context.hasModule(BusinessModule.FISCAL)) add(HomeActionId.FISCAL)
            }
            return HomeConfiguration(actions, capabilities)
        }
    }
}
