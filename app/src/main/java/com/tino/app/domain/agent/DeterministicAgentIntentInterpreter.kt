package com.tino.app.domain.agent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Explicit terminal boundary for language the local routers cannot classify.
 * Known operations are resolved before this boundary; unknown operations ask
 * the user to clarify instead of invoking an opaque model.
 */
@Singleton
class DeterministicAgentIntentInterpreter @Inject constructor() : AgentIntentInterpreter {
    override suspend fun interpret(input: String): AgentIntentResult =
        AgentIntentResult.Unsupported(
            reason = "UNSUPPORTED_INTENT",
            userMessage = "Não consegui determinar uma operação segura. Tente dizer o que deseja consultar ou registrar.",
        )

    override suspend fun interpret(
        input: String,
        availableCapabilities: Set<TinoCapabilityId>,
    ): AgentIntentResult = interpret(input)
}
