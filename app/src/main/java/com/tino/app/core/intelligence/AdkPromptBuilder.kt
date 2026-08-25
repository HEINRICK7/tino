package com.tino.app.core.intelligence

import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.TinoIntelligenceToolRegistry
import com.tino.app.domain.intelligence.planning.IntelligenceGoal
import org.json.JSONObject

internal object AdkPromptBuilder {
    fun instruction(): String = """
        Você é o planejador do TINO. Não responda ao comerciante e não execute ferramentas.
        Retorne SOMENTE um objeto JSON válido, sem markdown, sem texto antes ou depois.
        O JSON deve seguir exatamente este formato:
        {"goal":"GOAL","steps":[{"tool":"TOOL_NAME","purpose":"WHY"}],"requires_clarification":false,"confidence":0.0}
        GOAL permitido: ${IntelligenceGoal.entries.joinToString(",") { it.name }}.
        Use apenas ferramentas presentes no catálogo recebido. Não invente ferramentas, argumentos,
        clientes, produtos, valores ou fatos. O plano é somente leitura e analítico; mutações não são permitidas.
        Para uma pergunta desconhecida use goal UNSUPPORTED, steps [] e confidence 0.2.
    """.trimIndent()

    fun request(request: IntelligenceRequest): String = buildString {
        append("PERGUNTA\n")
        append(JSONObject.quote(request.utterance))
        append("\nCONTEXTO_DE_TELA\n")
        append(JSONObject.quote(request.screenContext ?: ""))
        append("\nCONTEXTO_RESOLVIDO\n")
        append(JSONObject.quote(request.resolvedContext.toString()))
        append("\nCAPACIDADES_DISPONIVEIS\n")
        append(request.availableCapabilities.joinToString(","))
        append("\nPERFIL_E_LOCALE\n")
        append(request.locale)
        append("\nCATALOGO_DE_TOOLS\n")
        append(TinoIntelligenceToolRegistry.all.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}; input=${tool.inputSchema}; " +
                "output=${tool.outputSchema}; kind=${tool.kind}"
        })
    }
}
