package com.tino.app.core.intelligence

import com.tino.app.domain.intelligence.planning.IntelligenceGoal
import com.tino.app.domain.intelligence.planning.IntelligencePlan
import com.tino.app.domain.intelligence.planning.IntelligencePlanStep

internal object AdkPlanParser {
    private val goalPattern = Regex("""\"goal\"\s*:\s*\"([^\"]+)\"""")
    private val stepPattern = Regex(
        """\{\s*\"tool\"\s*:\s*\"([^\"]+)\"\s*,\s*\"purpose\"\s*:\s*\"([^\"]*)\"\s*\}""",
    )
    private val clarificationPattern = Regex("""\"requires_clarification\"\s*:\s*(true|false)""")
    private val confidencePattern = Regex("""\"confidence\"\s*:\s*([0-9]+(?:\.[0-9]+)?)""")

    fun parse(raw: String): IntelligencePlan? {
        val goal = goalPattern.find(raw)?.groupValues?.getOrNull(1)?.uppercase()?.let { name ->
            IntelligenceGoal.entries.firstOrNull { it.name == name }
        } ?: return null
        val steps = stepPattern.findAll(raw).map { match ->
            IntelligencePlanStep(
                toolName = match.groupValues[1],
                purpose = match.groupValues[2].ifBlank { "executar etapa do plano" },
            )
        }.toList()
        val clarification = clarificationPattern.find(raw)?.groupValues?.getOrNull(1) == "true"
        val confidence = confidencePattern.find(raw)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?.coerceIn(0f, 1f) ?: 0.2f
        return IntelligencePlan(
            goal = goal,
            steps = steps,
            requiresClarification = clarification,
            confidence = confidence,
        )
    }
}
