package com.tino.app.domain.language

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable domain boundary for language interpretation.
 *
 * The implementation is deterministic and returns references instead of IDs or
 * persisted facts.
 */
@Singleton
class TinoLanguageRuntime @Inject constructor(
    private val deterministicInterpreter: DeterministicLanguageInterpreter,
) : LanguageIntentInterpreter {
    override suspend fun interpret(input: LanguageInput): IntentInterpretation? =
        deterministicInterpreter.interpret(input)
}
