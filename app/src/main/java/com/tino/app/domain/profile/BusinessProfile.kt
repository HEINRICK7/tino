package com.tino.app.domain.profile

import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoCapabilityRegistry

/** Business segments are configuration of one TINO app, not separate APKs. */
enum class BusinessVertical {
    RETAIL,
    BAKERY,
    RESTAURANT,
    STORE,
    OTHER,
}

/** User-facing business type kept as a compatibility name during migration. */
typealias BusinessType = BusinessVertical

/** Composable operating patterns; these are not vertical-specific screens. */
enum class OperationalPattern {
    TURNOVER_COMMERCE,
    PRODUCTION_AND_SALES,
    FOOD_SERVICE,
    SERVICES_WITH_APPOINTMENTS,
    SERVICES_WITH_WORK_ORDER,
    GENERAL,
}

enum class BusinessModule {
    CORE,
    SALES,
    INVENTORY,
    CUSTOMERS,
    CREDIT,
    STOCK_ENTRY,
    FISCAL,
    RETAIL,
    BAKERY,
    RESTAURANT,
    STORE,
}

data class BusinessProfile(
    val primaryVertical: BusinessVertical,
    val enabledModules: Set<BusinessModule>,
    val storeName: String = "",
    val ownerName: String? = null,
    val phone: String? = null,
    val version: Int = CURRENT_VERSION,
    val operationalPatterns: Set<OperationalPattern> = emptySet(),
    val permanentCapabilities: Set<TinoCapabilityId> = emptySet(),
) {
    init {
        require(BusinessModule.CORE in enabledModules) { "Todo perfil precisa do módulo CORE." }
        BusinessProfileValidator.validate(this)
    }

    fun has(module: BusinessModule): Boolean = module in enabledModules

    /** Legacy profiles may not have patterns yet; resolution supplies the safe preset. */
    fun effectiveOperationalPatterns(): Set<OperationalPattern> =
        operationalPatterns.ifEmpty { OperationalPatternCatalog.forVertical(primaryVertical) }

    companion object {
        fun retail(): BusinessProfile = BusinessProfile(
            primaryVertical = BusinessVertical.RETAIL,
            enabledModules = setOf(BusinessModule.CORE, BusinessModule.RETAIL),
        )

        const val CURRENT_VERSION = 1
    }
}

data class VerticalPreset(
    val vertical: BusinessVertical,
    val defaultModules: Set<BusinessModule>,
)

object VerticalPresetCatalog {
    private val sharedRetailModules = setOf(
        BusinessModule.CORE,
        BusinessModule.SALES,
        BusinessModule.INVENTORY,
        BusinessModule.CUSTOMERS,
        BusinessModule.CREDIT,
        BusinessModule.STOCK_ENTRY,
        BusinessModule.FISCAL,
    )

    val all: List<VerticalPreset> = BusinessVertical.values().map { vertical ->
        VerticalPreset(
            vertical = vertical,
            // These verticals share only capabilities that already exist. Packs específicos ficam para depois.
            defaultModules = if (vertical == BusinessVertical.OTHER) {
                setOf(BusinessModule.CORE, BusinessModule.CUSTOMERS)
            } else {
                sharedRetailModules
            },
        )
    }

    fun forVertical(vertical: BusinessVertical): VerticalPreset = all.first { it.vertical == vertical }
}

object OperationalPatternCatalog {
    fun forVertical(vertical: BusinessVertical): Set<OperationalPattern> = when (vertical) {
        BusinessVertical.RETAIL,
        BusinessVertical.STORE,
        -> setOf(OperationalPattern.TURNOVER_COMMERCE)
        BusinessVertical.BAKERY -> setOf(OperationalPattern.PRODUCTION_AND_SALES)
        BusinessVertical.RESTAURANT -> setOf(OperationalPattern.FOOD_SERVICE)
        BusinessVertical.OTHER -> setOf(OperationalPattern.GENERAL)
    }
}

object BusinessProfileValidator {
    private val dependencies = mapOf(
        BusinessModule.CREDIT to setOf(BusinessModule.CUSTOMERS),
        BusinessModule.STOCK_ENTRY to setOf(BusinessModule.INVENTORY),
    )

    fun validate(profile: BusinessProfile) {
        require(profile.version == BusinessProfile.CURRENT_VERSION) { "Versão de perfil não suportada." }
        require(BusinessModule.CORE in profile.enabledModules) { "Todo perfil precisa do módulo CORE." }
        require(profile.enabledModules.all(TinoModuleRegistry::has)) {
            "O perfil contém um módulo sem definição no registry."
        }
        require(profile.permanentCapabilities.all { it in TinoCapabilityRegistry.all }) {
            "O perfil contém uma capability permanente desconhecida."
        }
        require(profile.permanentCapabilities.none {
            TinoCapabilityRegistry.require(it).type == com.tino.app.domain.agent.TinoCapabilityType.MUTATION
        }) {
            "Mutações não podem ser ativadas permanentemente pelo perfil."
        }
        dependencies.forEach { (module, required) ->
            if (module in profile.enabledModules) {
                require(required.all(profile.enabledModules::contains)) {
                    "$module exige: ${required.joinToString()}"
                }
            }
        }
    }
}

data class TinoVerticalModule(
    val id: BusinessModule,
    val supportedVerticals: Set<BusinessVertical>,
    val capabilities: Set<TinoCapabilityId>,
    val vocabulary: Set<String>,
    val analytics: Set<String>,
)

object TinoModuleRegistry {
    val sales = TinoVerticalModule(
        id = BusinessModule.SALES,
        supportedVerticals = BusinessVertical.values().toSet(),
        capabilities = setOf(TinoCapabilityId.NAVIGATE, TinoCapabilityId.READ_FINANCIAL_SUMMARY),
        vocabulary = setOf("venda", "vendas"),
        analytics = setOf("sales"),
    )
    val inventory = TinoVerticalModule(
        id = BusinessModule.INVENTORY,
        supportedVerticals = BusinessVertical.values().toSet(),
        capabilities = setOf(TinoCapabilityId.LIST_PRODUCTS, TinoCapabilityId.REPLENISHMENT_QUERY, TinoCapabilityId.GET_PRODUCT_STOCK, TinoCapabilityId.GET_PRODUCT_PRICE),
        vocabulary = setOf("produto", "estoque"),
        analytics = setOf("stock_velocity", "days_of_coverage"),
    )
    val customers = TinoVerticalModule(
        id = BusinessModule.CUSTOMERS,
        supportedVerticals = BusinessVertical.values().toSet(),
        capabilities = setOf(TinoCapabilityId.LIST_CUSTOMERS, TinoCapabilityId.GET_CUSTOMER_CONTACT, TinoCapabilityId.CREATE_CUSTOMER, TinoCapabilityId.SEARCH_CUSTOMER),
        vocabulary = setOf("cliente", "clientes"),
        analytics = emptySet(),
    )
    val credit = TinoVerticalModule(
        id = BusinessModule.CREDIT,
        supportedVerticals = BusinessVertical.values().toSet(),
        capabilities = setOf(
            TinoCapabilityId.ADD_CREDIT,
            TinoCapabilityId.ADD_CREDIT_ITEM,
            TinoCapabilityId.LIST_RECEIVABLES,
            TinoCapabilityId.LIST_OVERDUE,
            TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
        ),
        vocabulary = setOf("fiado", "devedor"),
        analytics = setOf("receivables"),
    )
    val stockEntry = TinoVerticalModule(
        id = BusinessModule.STOCK_ENTRY,
        supportedVerticals = BusinessVertical.values().toSet(),
        capabilities = setOf(TinoCapabilityId.REGISTER_STOCK_ENTRY, TinoCapabilityId.LIST_SUPPLIERS),
        vocabulary = setOf("entrada", "mercadoria"),
        analytics = emptySet(),
    )
    val fiscal = TinoVerticalModule(
        id = BusinessModule.FISCAL,
        supportedVerticals = BusinessVertical.values().toSet(),
        capabilities = emptySet(),
        vocabulary = setOf("nota", "fiscal"),
        analytics = emptySet(),
    )
    val retail: TinoVerticalModule = TinoVerticalModule(
        id = BusinessModule.RETAIL,
        supportedVerticals = setOf(BusinessVertical.RETAIL, BusinessVertical.STORE),
        capabilities = setOf(
            TinoCapabilityId.LIST_PRODUCTS,
            TinoCapabilityId.REPLENISHMENT_QUERY,
            TinoCapabilityId.GET_PRODUCT_STOCK,
            TinoCapabilityId.GET_PRODUCT_PRICE,
            TinoCapabilityId.LIST_CUSTOMERS,
            TinoCapabilityId.GET_CUSTOMER_CONTACT,
            TinoCapabilityId.CREATE_CUSTOMER,
            TinoCapabilityId.CHANGE_PRODUCT_PRICE,
            TinoCapabilityId.LIST_SUPPLIERS,
            TinoCapabilityId.LIST_RECEIVABLES,
            TinoCapabilityId.RECEIVE_CREDIT_PAYMENT,
            TinoCapabilityId.REGISTER_STOCK_ENTRY,
        ),
        vocabulary = setOf("produto", "estoque", "cliente", "fiado", "venda"),
        analytics = setOf("stock_velocity", "days_of_coverage", "receivables"),
    )

    /** Vertical markers compose existing capabilities; they do not create new screens or packs. */
    val bakery = TinoVerticalModule(
        id = BusinessModule.BAKERY,
        supportedVerticals = setOf(BusinessVertical.BAKERY),
        capabilities = setOf(
            TinoCapabilityId.READ_FINANCIAL_SUMMARY,
            TinoCapabilityId.LIST_PRODUCTS,
            TinoCapabilityId.GET_PRODUCT_STOCK,
            TinoCapabilityId.REPLENISHMENT_QUERY,
            TinoCapabilityId.LIST_CUSTOMERS,
        ),
        vocabulary = setOf("padaria", "produção", "receita"),
        analytics = setOf("sales", "stock_velocity"),
    )
    val restaurant = TinoVerticalModule(
        id = BusinessModule.RESTAURANT,
        supportedVerticals = setOf(BusinessVertical.RESTAURANT),
        capabilities = setOf(
            TinoCapabilityId.READ_FINANCIAL_SUMMARY,
            TinoCapabilityId.LIST_CUSTOMERS,
        ),
        vocabulary = setOf("restaurante", "mesa", "comanda"),
        analytics = setOf("sales"),
    )
    val store = retail.copy(
        id = BusinessModule.STORE,
        supportedVerticals = setOf(BusinessVertical.STORE),
        vocabulary = retail.vocabulary + "loja",
    )

    private val modules = listOf(
        sales,
        inventory,
        customers,
        credit,
        stockEntry,
        fiscal,
        retail,
        bakery,
        restaurant,
        store,
    ).associateBy { it.id }

    fun has(module: BusinessModule): Boolean = module == BusinessModule.CORE || module in modules

    fun definitionFor(module: BusinessModule): TinoVerticalModule? = modules[module]

    fun forProfile(profile: BusinessProfile): List<TinoVerticalModule> = BusinessModule.values()
        .filter(profile.enabledModules::contains)
        .mapNotNull(modules::get)

    fun isEnabled(profile: BusinessProfile, module: BusinessModule): Boolean =
        module == BusinessModule.CORE || module in profile.enabledModules

    fun capabilitiesFor(profile: BusinessProfile): Set<TinoCapabilityId> = forProfile(profile)
        .flatMap { module -> module.capabilities }
        .toSet()
}

interface ActiveCapabilityResolver {
    fun resolve(profile: BusinessProfile): Set<TinoCapabilityId>
}

class DefaultActiveCapabilityResolver : ActiveCapabilityResolver {
    private val contextResolver = DefaultBusinessContextResolver()

    override fun resolve(profile: BusinessProfile): Set<TinoCapabilityId> =
        contextResolver.resolve(profile).capabilities
}
