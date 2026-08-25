package com.tino.app.domain.profile

import com.tino.app.domain.agent.TinoCapabilityId
import com.tino.app.domain.agent.TinoCapabilityRegistry

enum class ActivationMode { PERMANENT, EPHEMERAL }

object CapabilityRecoveryPolicy {
    fun canActivatePermanently(capability: TinoCapabilityId): Boolean {
        val descriptor = TinoCapabilityRegistry.all[capability] ?: return false
        return descriptor.type != com.tino.app.domain.agent.TinoCapabilityType.MUTATION
    }
}

data class CapabilityActivation(
    val capability: TinoCapabilityId,
    val mode: ActivationMode,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val source: String,
) {
    fun isActiveAt(nowEpochMs: Long): Boolean =
        mode == ActivationMode.PERMANENT || expiresAtEpochMs == null || expiresAtEpochMs > nowEpochMs
}

data class OperationalPatternDescriptor(
    val id: OperationalPattern,
    val vocabulary: Set<String>,
    val contextRules: Set<String>,
)

data class ResolvedBusinessContext(
    val profile: BusinessProfile,
    val patterns: Set<OperationalPattern>,
    val modules: List<TinoVerticalModule>,
    val capabilities: Set<TinoCapabilityId>,
    val vocabulary: Set<String>,
    val analytics: Set<String>,
    val allowedA2uiComponents: Set<String>,
    val activations: List<CapabilityActivation>,
) {
    fun hasCapability(capability: TinoCapabilityId): Boolean = capability in capabilities

    fun hasModule(module: BusinessModule): Boolean =
        module == BusinessModule.CORE || module in profile.enabledModules

    fun isEphemeral(capability: TinoCapabilityId): Boolean = activations.any {
        it.capability == capability && it.mode == ActivationMode.EPHEMERAL
    }
}

interface BusinessContextResolver {
    fun resolve(
        profile: BusinessProfile,
        activations: List<CapabilityActivation> = emptyList(),
        nowEpochMs: Long = Long.MAX_VALUE,
    ): ResolvedBusinessContext
}

/** Single composition boundary consumed by Agent, Home, Quick Queries and navigation. */
class DefaultBusinessContextResolver : BusinessContextResolver {
    override fun resolve(
        profile: BusinessProfile,
        activations: List<CapabilityActivation>,
        nowEpochMs: Long,
    ): ResolvedBusinessContext {
        BusinessProfileValidator.validate(profile)
        val modules = TinoModuleRegistry.forProfile(profile)
        val validActivations = activations.filter { activation ->
            activation.isActiveAt(nowEpochMs)
        }
        val moduleCapabilities = TinoModuleRegistry.capabilitiesFor(profile)
        val activatedCapabilities = validActivations
            .filter { it.mode == ActivationMode.EPHEMERAL }
            .map { it.capability }
            .toSet()
        val capabilities = (
            moduleCapabilities + profile.permanentCapabilities + activatedCapabilities
            ).filterTo(linkedSetOf()) { it in TinoCapabilityRegistry.all }
        val descriptors = capabilities.mapNotNull(TinoCapabilityRegistry.all::get)
        val patternDescriptors = profile.effectiveOperationalPatterns().map(::patternDescriptor)

        return ResolvedBusinessContext(
            profile = profile,
            patterns = profile.effectiveOperationalPatterns(),
            modules = modules,
            capabilities = capabilities,
            vocabulary = modules.flatMapTo(linkedSetOf()) { it.vocabulary } +
                patternDescriptors.flatMapTo(linkedSetOf()) { it.vocabulary },
            analytics = modules.flatMapTo(linkedSetOf()) { it.analytics },
            allowedA2uiComponents = descriptors.mapNotNullTo(linkedSetOf()) { it.a2uiComponent },
            activations = validActivations,
        )
    }

    private fun patternDescriptor(pattern: OperationalPattern): OperationalPatternDescriptor = when (pattern) {
        OperationalPattern.TURNOVER_COMMERCE -> OperationalPatternDescriptor(
            pattern,
            setOf("venda", "estoque", "cliente", "fiado"),
            setOf("sales", "inventory", "receivables"),
        )
        OperationalPattern.PRODUCTION_AND_SALES -> OperationalPatternDescriptor(
            pattern,
            setOf("produção", "venda", "estoque"),
            setOf("production", "inventory"),
        )
        OperationalPattern.FOOD_SERVICE -> OperationalPatternDescriptor(
            pattern,
            setOf("mesa", "comanda", "venda"),
            setOf("sales", "service"),
        )
        OperationalPattern.SERVICES_WITH_APPOINTMENTS -> OperationalPatternDescriptor(
            pattern,
            setOf("agenda", "serviço", "cliente"),
            setOf("appointments", "customers"),
        )
        OperationalPattern.SERVICES_WITH_WORK_ORDER -> OperationalPatternDescriptor(
            pattern,
            setOf("ordem", "serviço", "cliente"),
            setOf("work_orders", "customers"),
        )
        OperationalPattern.GENERAL -> OperationalPatternDescriptor(
            pattern,
            setOf("cliente", "venda"),
            setOf("sales", "customers"),
        )
    }
}
