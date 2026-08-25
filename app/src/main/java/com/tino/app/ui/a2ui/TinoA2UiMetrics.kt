package com.tino.app.ui.a2ui

import java.util.concurrent.atomic.AtomicLong

/** Process-local counters used to catch visual contract regressions without exposing debug data. */
object TinoA2UiMetrics {
    const val GENERIC_FALLBACK_RATE = "GENERIC_FALLBACK_RATE"
    const val A2UI_UNKNOWN_COMPONENT_RATE = "A2UI_UNKNOWN_COMPONENT_RATE"

    private val rendered = AtomicLong()
    private val fallbacks = AtomicLong()
    private val unknownComponents = AtomicLong()

    fun recordRendered() {
        rendered.incrementAndGet()
    }

    fun recordFallback(unknownComponent: Boolean) {
        fallbacks.incrementAndGet()
        if (unknownComponent) unknownComponents.incrementAndGet()
    }

    fun snapshot(): Snapshot = Snapshot(
        rendered = rendered.get(),
        fallbacks = fallbacks.get(),
        unknownComponents = unknownComponents.get(),
    )

    data class Snapshot(
        val rendered: Long,
        val fallbacks: Long,
        val unknownComponents: Long,
    ) {
        val genericFallbackRate: Float
            get() = if (rendered == 0L) 0f else fallbacks.toFloat() / rendered

        val unknownComponentRate: Float
            get() = if (rendered == 0L) 0f else unknownComponents.toFloat() / rendered
    }
}
