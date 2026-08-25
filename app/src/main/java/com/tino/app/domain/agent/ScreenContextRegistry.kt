package com.tino.app.domain.agent

import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the latest structured context for each application surface. */
@Singleton
class ScreenContextRegistry @Inject constructor() {
    private val contexts = mutableMapOf<String, ScreenAgentContext>()

    fun register(context: ScreenAgentContext) {
        contexts[context.screen] = context
    }

    fun contextFor(screen: String): ScreenAgentContext? = contexts[screen]

    fun clear() = contexts.clear()
}
