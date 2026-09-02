package com.tino.app.domain.agent

import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.observability.NoOpAuditLogger
import com.tino.app.interfaceadapter.a2ui.FinancialSummaryA2uiMapper
import com.tino.app.interfaceadapter.a2ui.CommerceActionA2uiMapper
import com.tino.app.interfaceadapter.a2ui.CustomerBalanceA2uiMapper
import com.tino.app.interfaceadapter.a2ui.CustomerTimelineA2uiMapper
import com.tino.app.interfaceadapter.a2ui.EntityChoiceA2uiMapper
import com.tino.app.interfaceadapter.a2ui.DbFirstReadA2uiMapper
import com.tino.app.interfaceadapter.a2ui.IntelligenceA2uiMapper
import com.tino.app.domain.intelligence.IntelligenceRequest
import com.tino.app.domain.intelligence.IntelligenceResponseStatus
import com.tino.app.domain.intelligence.IntelligenceRuntimePort
import com.tino.app.domain.intelligence.UnavailableIntelligenceRuntime
import com.tino.app.domain.commerce.PaymentMethod
import com.tino.app.domain.voice.ToolCall
import com.tino.app.domain.voice.MutationConfirmation
import com.tino.app.domain.language.CommerceContextMemory
import com.tino.app.domain.language.ContextualLanguageInterpreter
import com.tino.app.domain.language.ContextTurnClassification
import com.tino.app.domain.language.ContextReferenceSource
import com.tino.app.domain.language.DeterministicLanguageInterpreter
import com.tino.app.domain.language.IntentInterpretation
import com.tino.app.domain.language.LanguageEntityType
import com.tino.app.domain.language.LanguageInput
import com.tino.app.domain.language.LanguageSource
import com.tino.app.domain.language.TinoIntent
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class AgenticTextQueryCoordinator @Inject constructor(
    private val interpreter: AgentIntentInterpreter,
    private val boundary: AgentQueryBoundary,
    private val a2uiMapper: FinancialSummaryA2uiMapper,
    private val actionMapper: CommerceActionA2uiMapper,
    private val customerBalanceMapper: CustomerBalanceA2uiMapper,
    private val customerTimelineMapper: CustomerTimelineA2uiMapper,
    private val entityChoiceMapper: EntityChoiceA2uiMapper,
    private val fastIntentRouter: FastIntentRouter,
    private val commandIntentRouter: CommandIntentRouter,
    private val dbFirstReadMapper: DbFirstReadA2uiMapper,
    private val agentSession: TinoAgentSession,
    private val activityLedger: AgentActivityLedger,
    private val languageContextMemory: CommerceContextMemory,
    private val interactionLedger: AgentInteractionLedger,
    private val intelligenceRuntime: IntelligenceRuntimePort,
    private val intelligenceA2uiMapper: IntelligenceA2uiMapper,
    private val auditLogger: AuditLogger,
    private val progressRuntime: AgentProgressRuntime,
    private val streamingRuntime: AgentStreamingRuntime,
) : AgenticTextQueryPort {
    private val globalCommandRouter = com.tino.app.domain.voice.GlobalCommandRouter()
    private val contextualLanguageInterpreter = ContextualLanguageInterpreter(
        base = DeterministicLanguageInterpreter(),
        memory = languageContextMemory,
    )
    private val interruptCorrectionRuntime = InterruptCorrectionRuntime(agentSession)
    constructor(
        interpreter: AgentIntentInterpreter,
        boundary: AgentQueryBoundary,
        a2uiMapper: FinancialSummaryA2uiMapper,
    ) : this(
        interpreter,
        boundary,
        a2uiMapper,
        CommerceActionA2uiMapper(),
        CustomerBalanceA2uiMapper(),
        CustomerTimelineA2uiMapper(),
        EntityChoiceA2uiMapper(),
        FastIntentRouter(),
        CommandIntentRouter(),
        DbFirstReadA2uiMapper(),
        TinoAgentSession(),
        AgentActivityLedger(),
        CommerceContextMemory(),
        AgentInteractionLedger(),
        UnavailableIntelligenceRuntime(),
        IntelligenceA2uiMapper(),
        NoOpAuditLogger,
        AgentProgressRuntime(),
        AgentStreamingRuntime(),
    )

    constructor(
        interpreter: AgentIntentInterpreter,
        boundary: AgentQueryBoundary,
        a2uiMapper: FinancialSummaryA2uiMapper,
        actionMapper: CommerceActionA2uiMapper,
    ) : this(
        interpreter,
        boundary,
        a2uiMapper,
        actionMapper,
        CustomerBalanceA2uiMapper(),
        CustomerTimelineA2uiMapper(),
        EntityChoiceA2uiMapper(),
        FastIntentRouter(),
        CommandIntentRouter(),
        DbFirstReadA2uiMapper(),
        TinoAgentSession(),
        AgentActivityLedger(),
        CommerceContextMemory(),
        AgentInteractionLedger(),
        UnavailableIntelligenceRuntime(),
        IntelligenceA2uiMapper(),
        NoOpAuditLogger,
        AgentProgressRuntime(),
        AgentStreamingRuntime(),
    )

    override suspend fun ask(input: String): AgentA2uiResponse =
        runWithRuntimeSignals(input) { askInternal(input) }

    /**
     * All query entry points share the same progress lifecycle. This keeps
     * voice, text, quick queries, entity continuation and confirmations from
     * creating invisible executions outside M2.
     */
    private suspend fun <T : AgentA2uiResponse> runWithRuntimeSignals(
        input: String? = null,
        operation: suspend () -> T,
    ): T {
        // A voice session may already have emitted speech/partial/final
        // events. Reuse that stream so the same run reaches A2UI terminally.
        val runId = streamingRuntime.activeRunIdOrNull() ?: "agent-${UUID.randomUUID()}"
        val executionId = "query-${UUID.randomUUID()}"
        progressRuntime.start(runId, executionId)
        val lastStreamEvent = streamingRuntime.snapshot.value.lastEvent
        if (input != null && (lastStreamEvent?.runId != runId || lastStreamEvent.type != AgentStreamEventType.TRANSCRIPT_COMMITTED)) {
            streamingRuntime.emit(
                runId,
                AgentStreamEventType.TRANSCRIPT_COMMITTED,
                mapOf("text_length" to input.length.toString(), "source" to "query"),
            )
        }
        streamingRuntime.emit(runId, AgentStreamEventType.AGENT_STARTED)
        progressRuntime.toolStarted("agentic_query")
        return try {
            val response = operation()
            responseCapability(response)?.let { progressRuntime.capabilityStarted(it) }
            when (response) {
                is AgentA2uiResponse.EntityChoice -> {
                    progressRuntime.waitingForUser("Escolha uma opção para continuar.")
                    streamingRuntime.emit(
                        runId,
                        AgentStreamEventType.STATE_CHANGED,
                        mapOf("state" to AgentVoiceState.NEEDS_CLARIFICATION.name),
                    )
                }
                is AgentA2uiResponse.ActionPreview -> {
                    progressRuntime.waitingForUser("Confirmação necessária.")
                    streamingRuntime.emit(
                        runId,
                        AgentStreamEventType.STATE_CHANGED,
                        mapOf("state" to AgentVoiceState.PREVIEW_READY.name),
                    )
                }
                is AgentA2uiResponse.Unsupported -> {
                    progressRuntime.toolCompleted("agentic_query", succeeded = false)
                    progressRuntime.fail(response.message)
                    streamingRuntime.close(runId, AgentStreamEventType.FAILED, mapOf("reason" to response.message))
                    agentSession.markFailed()
                    return response
                }
                else -> {
                    progressRuntime.toolCompleted("agentic_query", succeeded = true)
                    progressRuntime.complete()
                    streamingRuntime.emit(runId, AgentStreamEventType.TOOL_COMPLETED)
                    streamingRuntime.emit(runId, AgentStreamEventType.A2UI_UPDATED)
                    streamingRuntime.close(runId, AgentStreamEventType.COMPLETED)
                    agentSession.markSuccess()
                }
            }
            response
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                progressRuntime.cancel()
                streamingRuntime.close(runId, AgentStreamEventType.CANCELLED)
                agentSession.cancel()
            }
            throw cancelled
        } catch (error: Throwable) {
            progressRuntime.toolCompleted("agentic_query", succeeded = false)
            progressRuntime.fail(error.message ?: "Falha no Agent Runtime.")
            streamingRuntime.close(
                runId,
                AgentStreamEventType.FAILED,
                mapOf("reason" to (error.message ?: "unknown")),
            )
            agentSession.markFailed()
            throw error
        }
    }

    private fun responseCapability(response: AgentA2uiResponse): TinoCapabilityId? = when (response) {
        is AgentA2uiResponse.Ready -> response.intent.capability.toTinoCapabilityId()
        is AgentA2uiResponse.CustomerBalanceReady -> response.intent.capability.toTinoCapabilityId()
        is AgentA2uiResponse.CustomerTimelineReady -> response.intent.capability.toTinoCapabilityId()
        is AgentA2uiResponse.ReadListReady -> response.intent.capability.toTinoCapabilityId()
        is AgentA2uiResponse.IntelligenceReady -> null
        is AgentA2uiResponse.EntityChoice -> response.intent.capability.toTinoCapabilityId()
        is AgentA2uiResponse.ActionPreview -> response.intent.capability.toTinoCapabilityId()
        is AgentA2uiResponse.ActionCompleted -> response.intent.capability.toTinoCapabilityId()
        is AgentA2uiResponse.Unsupported -> null
    }

    private suspend fun askInternal(input: String): AgentA2uiResponse {
        val startedAt = System.nanoTime()
        auditLogger.record(AuditEventType.VOICE_STAGE, mapOf("stage" to "ROUTING_STARTED"))
        val languageStartedAt = System.nanoTime()
        val sessionSnapshot = agentSession.snapshot.value
        sessionSnapshot.sessionMemory.currentScreen.let { persistedScreen ->
            val screenContext = if (persistedScreen.screen == "UNKNOWN") {
                sessionSnapshot.screenContext
            } else {
                persistedScreen.copy(
                    secondaryEntities = (
                        persistedScreen.secondaryEntities + sessionSnapshot.sessionMemory.recentEntities
                    ).distinctBy { it.type to it.text }.take(8),
                )
            }
            languageContextMemory.rememberScreen(
                screen = screenContext.screen,
                primaryEntity = screenContext.primaryEntity,
                secondaryEntities = screenContext.secondaryEntities,
            )
        }
        val classification = classifyTurn(input)
        interactionLedger.record(
            AgentInteractionTrace(classification = classification),
        )
        if (classification == ContextTurnClassification.CANCELLATION) {
            agentSession.cancel()
            languageContextMemory.clearConversationDraft()
            pendingCalls.clear()
            pendingConfirmations.clear()
            pendingPreview = null
            return AgentA2uiResponse.Unsupported(
                message = "Certo. Cancelei a operação.",
                latencyMs = elapsedMs(startedAt),
                intentLatencyMs = elapsedMs(languageStartedAt),
            )
        }
        if (classification == ContextTurnClassification.CONFIRMATION) {
            val preview = pendingPreview
            // The enclosing ask() already owns this run's progress lifecycle.
            // Calling the public confirm() here would incorrectly start a
            // nested execution and violate M2's single-active-run invariant.
            if (preview != null) return confirmInternal(preview)
            return AgentA2uiResponse.Unsupported(
                message = "Não há operação aguardando confirmação.",
                latencyMs = elapsedMs(startedAt),
                intentLatencyMs = elapsedMs(languageStartedAt),
            )
        }
        if (isDraftTotalQuestion(input) && pendingPreview != null) {
            return pendingPreview!!
        }
        val languageInterpretation = contextualLanguageInterpreter
            .interpret(LanguageInput(input, source = LanguageSource.TEXT))
        languageInterpretation?.let { interpretation ->
            agentSession.rememberIntent(
                intent = interpretation.intent,
                entities = interpretation.references,
                source = interpretation.referenceSources.values.firstOrNull(),
            )
        }
        val languageIntent = languageInterpretation?.toAgentIntent()
        val fastRouterStartedAt = System.nanoTime()
        when (val fast = fastIntentRouter.route(input)) {
            is FastIntentResult.Match -> {
                recordRoutingCompleted(startedAt, "fast", true)
                return mapBoundaryResponse(
                    intent = fast.intent,
                    startedAt = startedAt,
                    intentLatencyMs = elapsedMs(fastRouterStartedAt),
                    fastRouterHit = true,
                    fastRouterMs = elapsedMs(fastRouterStartedAt),
                )
            }
            FastIntentResult.NoMatch -> Unit
        }
        val fastRouterMs = elapsedMs(fastRouterStartedAt)
        val commandRouterStartedAt = System.nanoTime()
        when (val command = commandIntentRouter.route(input)) {
            is CommandIntentResult.Match -> {
                recordRoutingCompleted(startedAt, "command", false)
                return mapBoundaryResponse(
                    intent = command.intent,
                    startedAt = startedAt,
                    intentLatencyMs = elapsedMs(commandRouterStartedAt),
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = true,
                    commandRouterMs = elapsedMs(commandRouterStartedAt),
                )
            }
            CommandIntentResult.NoMatch -> Unit
        }
        val commandRouterMs = elapsedMs(commandRouterStartedAt)
        val globalRouterStartedAt = System.nanoTime()
        globalCommandRouter.route(input)?.let { globalCall ->
            recordRoutingCompleted(startedAt, "global", false)
            return mapBoundaryResponse(
                intent = AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = AgentCapability.GLOBAL_TOOL,
                    period = AgentIntentPeriod.TODAY,
                    globalToolCall = globalCall,
                ),
                startedAt = startedAt,
                intentLatencyMs = elapsedMs(globalRouterStartedAt),
                commandRouterMs = commandRouterMs,
                globalCall = globalCall,
            )
        }
        val protectedLanguageIntent = languageInterpretation?.intent
        if (protectedLanguageIntent == TinoIntent.CORRECTION) {
            val corrected = languageInterpretation?.let { applyCorrection(it) }
                ?: languageInterpretation?.let { buildPostExecutionCorrection(it) }
            if (corrected != null) {
                recordRoutingCompleted(startedAt, "correction", false)
                pendingCalls.clear()
                pendingConfirmations.clear()
                pendingPreview = null
                return mapBoundaryResponse(
                    intent = corrected,
                    startedAt = startedAt,
                    intentLatencyMs = elapsedMs(languageStartedAt),
                    globalCall = corrected.globalToolCall,
                )
            }
        }
        if (protectedLanguageIntent in setOf(TinoIntent.NEGATION, TinoIntent.COMPOUND)) {
            recordRoutingCompleted(startedAt, "language_guard", false)
            val safeInterpretation = requireNotNull(languageInterpretation)
            return AgentA2uiResponse.Unsupported(
                message = when (safeInterpretation.intent) {
                    TinoIntent.NEGATION -> "Certo. Não vou registrar essa operação."
                    else -> "Entendi mais de uma operação. Vou separar e revisar tudo antes de registrar."
                },
                latencyMs = elapsedMs(startedAt),
                intentLatencyMs = elapsedMs(languageStartedAt),
                fastRouterMs = fastRouterMs,
                commandRouterMs = commandRouterMs,
            )
        }
        if (languageIntent != null) {
            recordRoutingCompleted(startedAt, "language", false)
            return mapBoundaryResponse(
                intent = languageIntent,
                startedAt = startedAt,
                intentLatencyMs = elapsedMs(languageStartedAt),
            )
        }
        val intentStartedAt = System.nanoTime()
        auditLogger.record(AuditEventType.VOICE_STAGE, mapOf("stage" to "AGENT_STARTED"))
        val interpretation = try {
            interpreter.interpret(input, agentSession.availableCapabilities())
        } catch (error: Throwable) {
            auditLogger.record(
                AuditEventType.VOICE_STAGE,
                mapOf(
                    "stage" to "AGENT_COMPLETED",
                    "status" to "failed",
                    "duration_ms" to elapsedMs(intentStartedAt).toString(),
                ),
            )
            throw error
        }
        auditLogger.record(
            AuditEventType.VOICE_STAGE,
            mapOf(
                "stage" to "AGENT_COMPLETED",
                "status" to "success",
                "duration_ms" to elapsedMs(intentStartedAt).toString(),
            ),
        )
        val intentLatencyMs = elapsedMs(intentStartedAt)
        recordRoutingCompleted(startedAt, "interpreter", false)
        return when (interpretation) {
            is AgentIntentResult.Unsupported -> {
                val intelligence = intelligenceRuntime.execute(
                    IntelligenceRequest(
                        requestId = "agent-${System.nanoTime()}",
                        sessionId = "tino-agent",
                        utterance = input,
                        screenContext = agentSession.snapshot.value.screenContext.screen,
                        availableCapabilities = agentSession.availableCapabilities()
                            .map { it.name }
                            .toSet(),
                    ),
                )
                if (intelligence.status != IntelligenceResponseStatus.UNSUPPORTED) {
                    val a2uiStartedAt = System.nanoTime()
                    AgentA2uiResponse.IntelligenceReady(
                        response = intelligence,
                        message = intelligenceA2uiMapper.map(intelligence),
                        latencyMs = elapsedMs(startedAt),
                        intentLatencyMs = intentLatencyMs,
                        capabilityLatencyMs = elapsedMs(startedAt),
                        a2uiLatencyMs = elapsedMs(a2uiStartedAt),
                        fastRouterMs = fastRouterMs,
                        commandRouterMs = commandRouterMs,
                    )
                } else {
                    AgentA2uiResponse.Unsupported(
                        message = interpretation.userMessage,
                        debug = interpretation.debug,
                        latencyMs = elapsedMs(startedAt),
                        intentLatencyMs = intentLatencyMs,
                        fastRouterHit = false,
                        fastRouterMs = fastRouterMs,
                        commandRouterHit = false,
                        commandRouterMs = commandRouterMs,
                    )
                }
            }

            is AgentIntentResult.Supported -> {
                mapBoundaryResponse(
                    intent = interpretation.intent,
                    startedAt = startedAt,
                    intentLatencyMs = intentLatencyMs,
                    fastRouterHit = false,
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = false,
                    commandRouterMs = commandRouterMs,
                )
            }
        }
    }

    /** Executes a registered capability directly from a semantic UI action.
     * Quick Queries use this entry point instead of manufacturing an ASR phrase.
     */
    override suspend fun askCapability(capability: AgentCapability): AgentA2uiResponse =
        askCapability(capability, subjectId = null)

    override suspend fun askCapability(
        capability: AgentCapability,
        subjectId: String?,
    ): AgentA2uiResponse =
        runWithRuntimeSignals {
            mapBoundaryResponse(
                intent = AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = capability,
                    period = AgentIntentPeriod.TODAY,
                    productRef = subjectId?.takeIf {
                        capability in setOf(
                            AgentCapability.REPLENISHMENT_QUERY,
                            AgentCapability.GET_PRODUCT_STOCK,
                            AgentCapability.GET_PRODUCT_PRICE,
                            AgentCapability.LIST_PRODUCTS,
                        )
                    },
                    customerRef = subjectId?.takeIf {
                        capability in setOf(
                            AgentCapability.GET_CUSTOMER_BALANCE,
                            AgentCapability.GET_CUSTOMER_TIMELINE,
                            AgentCapability.GET_CUSTOMER_CONTACT,
                        )
                    },
                    supplierRef = subjectId?.takeIf {
                        capability == AgentCapability.LIST_SUPPLIERS
                    },
                ),
                startedAt = System.nanoTime(),
                intentLatencyMs = 0L,
            )
        }

    private fun IntentInterpretation.toAgentIntent(): AgentIntent? {
        fun reference(type: LanguageEntityType): String? = references
            .firstOrNull { it.type == type }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return when (intent) {
            TinoIntent.READ_FINANCIAL_SUMMARY -> AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.READ_FINANCIAL_SUMMARY,
                period = AgentIntentPeriod.TODAY,
            )
            TinoIntent.READ_RECEIVABLES -> AgentIntent(
                schemaVersion = AgentIntentSchema.VERSION,
                capability = AgentCapability.LIST_RECEIVABLES,
                period = AgentIntentPeriod.TODAY,
            )
            TinoIntent.ADD_CREDIT_ITEM -> {
                val customer = reference(LanguageEntityType.CUSTOMER) ?: return null
                val product = reference(LanguageEntityType.PRODUCT) ?: return null
                val quantity = quantity?.wholeUnits?.takeIf { it > 0 } ?: return null
                AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = AgentCapability.ADD_CREDIT_ITEM,
                    period = AgentIntentPeriod.TODAY,
                    customerRef = customer,
                    productRef = product,
                    quantity = quantity,
                )
            }
            TinoIntent.RECEIVE_CREDIT_PAYMENT -> {
                val customer = reference(LanguageEntityType.CUSTOMER) ?: return null
                val amount = amountCents?.takeIf { it > 0 } ?: return null
                AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = AgentCapability.REGISTER_CREDIT_PAYMENT,
                    period = AgentIntentPeriod.TODAY,
                    customerRef = customer,
                    amountCents = amount,
                    creditPaymentMethod = paymentMethod,
                )
            }
            TinoIntent.READ_STOCK -> {
                val product = reference(LanguageEntityType.PRODUCT) ?: return null
                AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = AgentCapability.GET_PRODUCT_STOCK,
                    period = AgentIntentPeriod.TODAY,
                    productRef = product,
                )
            }
            TinoIntent.READ_PRODUCT -> {
                val product = reference(LanguageEntityType.PRODUCT) ?: return null
                AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = AgentCapability.GET_PRODUCT_PRICE,
                    period = AgentIntentPeriod.TODAY,
                    productRef = product,
                )
            }
            TinoIntent.READ_CUSTOMER_BALANCE -> {
                val customer = reference(LanguageEntityType.CUSTOMER) ?: return null
                AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = AgentCapability.GET_CUSTOMER_BALANCE,
                    period = AgentIntentPeriod.TODAY,
                    customerRef = customer,
                )
            }
            TinoIntent.READ_CUSTOMER_TIMELINE -> {
                val customer = reference(LanguageEntityType.CUSTOMER) ?: return null
                AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = AgentCapability.GET_CUSTOMER_TIMELINE,
                    period = AgentIntentPeriod.TODAY,
                    customerRef = customer,
                )
            }
            TinoIntent.REGISTER_STOCK_ENTRY -> {
                val product = reference(LanguageEntityType.PRODUCT) ?: return null
                val quantity = quantity?.wholeUnits?.takeIf { it > 0 } ?: return null
                val unitCostCents = unitCostCents?.takeIf { it >= 0L } ?: return null
                AgentIntent(
                    schemaVersion = AgentIntentSchema.VERSION,
                    capability = AgentCapability.REGISTER_STOCK_ENTRY,
                    period = AgentIntentPeriod.TODAY,
                    productRef = product,
                    quantity = quantity,
                    unitCostCents = unitCostCents,
                )
            }
            TinoIntent.ADD_CREDIT,
            TinoIntent.SEARCH_PRODUCT,
            TinoIntent.SEARCH_CUSTOMER,
            TinoIntent.SEARCH_SUPPLIER,
            TinoIntent.CHANGE_PRICE,
            TinoIntent.CORRECTION,
            TinoIntent.NEGATION,
            TinoIntent.COMPOUND,
            -> null
        }
    }

    override suspend fun selectEntityChoice(
        choice: AgentA2uiResponse.EntityChoice,
        selectedLabel: String,
    ): AgentA2uiResponse = runWithRuntimeSignals {
        val safeLabel = selectedLabel.trim()
        require(safeLabel.isNotBlank()) { "A escolha não pode ficar vazia." }
        agentSession.clearClarification()
        val resumedIntent = when (choice.entityType) {
            "product" -> choice.intent.copy(
                productRef = safeLabel,
                globalToolCall = choice.intent.globalToolCall?.withArgument("product", safeLabel),
            )
            "payment_method" -> choice.intent.copy(
                creditPaymentMethod = paymentMethodForLabel(safeLabel),
                globalToolCall = choice.intent.globalToolCall?.withArgument("payment_method", safeLabel),
            )
            else -> choice.intent.copy(
                customerRef = safeLabel,
                globalToolCall = choice.intent.globalToolCall?.withArgument(choice.entityType, safeLabel),
            )
        }
        mapBoundaryResponse(
            intent = resumedIntent,
            startedAt = System.nanoTime(),
            intentLatencyMs = choice.intentLatencyMs,
            globalCall = resumedIntent.globalToolCall,
        )
    }

    private fun ToolCall.withArgument(key: String, value: String): ToolCall = copy(
        arguments = arguments + (key to value),
    )

    private fun paymentMethodForLabel(label: String): PaymentMethod = when {
        label.equals("dinheiro", ignoreCase = true) -> PaymentMethod.CASH
        label.equals("pix", ignoreCase = true) -> PaymentMethod.PIX
        label.equals("maquininha", ignoreCase = true) -> PaymentMethod.CARD
        else -> error("Forma de recebimento inválida.")
    }

    private suspend fun mapBoundaryResponse(
        intent: AgentIntent,
        startedAt: Long,
        intentLatencyMs: Long,
        fastRouterHit: Boolean = false,
        fastRouterMs: Long = 0L,
        commandRouterHit: Boolean = false,
        commandRouterMs: Long = 0L,
        globalCall: ToolCall? = null,
    ): AgentA2uiResponse {
        val requiredCapability = intent.capability.toTinoCapabilityId()
        val availableCapabilities = agentSession.availableCapabilities()
        if (requiredCapability != null && availableCapabilities.isNotEmpty() && requiredCapability !in availableCapabilities) {
            return AgentA2uiResponse.Unsupported(
                message = "Esse recurso não está ativo para este negócio.",
                debug = AgentIntentDebugInfo(
                    code = "CAPABILITY_DISABLED",
                    capability = requiredCapability.name,
                    observedKeys = emptySet(),
                ),
                latencyMs = elapsedMs(startedAt),
                intentLatencyMs = intentLatencyMs,
            )
        }
        val capabilityStartedAt = System.nanoTime()
        auditLogger.record(AuditEventType.VOICE_STAGE, mapOf("stage" to "CAPABILITY_STARTED"))
        val resumedGlobalCall = globalCall ?: intent.globalToolCall
        val response = resumedGlobalCall?.let { boundary.askGlobal(it) } ?: boundary.ask(intent)
        val capabilityLatencyMs = elapsedMs(capabilityStartedAt)
        auditLogger.record(
            AuditEventType.VOICE_STAGE,
            mapOf("stage" to "CAPABILITY_COMPLETED", "duration_ms" to capabilityLatencyMs.toString()),
        )
        return when (response) {
            is AgentResponse.SurfaceReady -> {
                val a2uiStartedAt = System.nanoTime()
                auditLogger.record(
                    AuditEventType.VOICE_STAGE,
                    mapOf("stage" to "A2UI_READY", "duration_ms" to elapsedMs(a2uiStartedAt).toString()),
                )
                AgentA2uiResponse.Ready(
                    intent = intent,
                    result = response.result,
                    message = a2uiMapper.map(response),
                    latencyMs = elapsedMs(startedAt),
                    intentLatencyMs = intentLatencyMs,
                    capabilityLatencyMs = capabilityLatencyMs,
                    a2uiLatencyMs = elapsedMs(a2uiStartedAt),
                    fastRouterHit = fastRouterHit,
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = commandRouterHit,
                    commandRouterMs = commandRouterMs,
                )
            }
            is AgentResponse.ActionPreviewReady -> {
                val a2uiStartedAt = System.nanoTime()
                val preview = AgentA2uiResponse.ActionPreview(
                    intent = intent,
                    call = response.call,
                    preview = mergeDraftPreview(response.preview, response.call),
                    message = actionMapper.preview(mergeDraftPreview(response.preview, response.call)),
                    latencyMs = elapsedMs(startedAt),
                    intentLatencyMs = intentLatencyMs,
                    capabilityLatencyMs = capabilityLatencyMs,
                    a2uiLatencyMs = elapsedMs(a2uiStartedAt),
                    fastRouterHit = fastRouterHit,
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = commandRouterHit,
                    commandRouterMs = commandRouterMs,
                )
                rememberPendingPreview(preview)
                preview
            }
            is AgentResponse.GlobalAnswerReady -> {
                val a2uiStartedAt = System.nanoTime()
                val activity = recordActivity(intent, response.result)
                AgentA2uiResponse.ActionCompleted(
                    intent = intent,
                    result = response.result,
                    message = actionMapper.completed(
                        response.result,
                        activity?.id,
                        activity?.undoState == AgentUndoState.AVAILABLE,
                    ),
                    activityId = activity?.id,
                    latencyMs = elapsedMs(startedAt),
                    intentLatencyMs = intentLatencyMs,
                    capabilityLatencyMs = capabilityLatencyMs,
                    a2uiLatencyMs = elapsedMs(a2uiStartedAt),
                    fastRouterHit = fastRouterHit,
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = commandRouterHit,
                    commandRouterMs = commandRouterMs,
                )
            }
            is AgentResponse.CustomerBalanceReady -> {
                val a2uiStartedAt = System.nanoTime()
                AgentA2uiResponse.CustomerBalanceReady(
                    intent = intent,
                    result = response.result,
                    message = customerBalanceMapper.map(response),
                    customerResolutionMs = response.customerResolutionMs,
                    latencyMs = elapsedMs(startedAt),
                    intentLatencyMs = intentLatencyMs,
                    capabilityLatencyMs = capabilityLatencyMs,
                    a2uiLatencyMs = elapsedMs(a2uiStartedAt),
                    fastRouterHit = fastRouterHit,
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = commandRouterHit,
                    commandRouterMs = commandRouterMs,
                )
            }
            is AgentResponse.CustomerTimelineReady -> {
                val a2uiStartedAt = System.nanoTime()
                AgentA2uiResponse.CustomerTimelineReady(
                    intent = intent,
                    result = response.result,
                    message = customerTimelineMapper.map(response),
                    customerResolutionMs = response.customerResolutionMs,
                    latencyMs = elapsedMs(startedAt),
                    intentLatencyMs = intentLatencyMs,
                    capabilityLatencyMs = capabilityLatencyMs,
                    a2uiLatencyMs = elapsedMs(a2uiStartedAt),
                    fastRouterHit = fastRouterHit,
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = commandRouterHit,
                    commandRouterMs = commandRouterMs,
                )
            }
            is AgentResponse.ReadListReady -> {
                val a2uiStartedAt = System.nanoTime()
                auditLogger.record(
                    AuditEventType.VOICE_STAGE,
                    mapOf("stage" to "A2UI_READY", "duration_ms" to elapsedMs(a2uiStartedAt).toString()),
                )
                AgentA2uiResponse.ReadListReady(
                    intent = intent,
                    result = response.result,
                    message = dbFirstReadMapper.map(response),
                    latencyMs = elapsedMs(startedAt),
                    intentLatencyMs = intentLatencyMs,
                    capabilityLatencyMs = capabilityLatencyMs,
                    a2uiLatencyMs = elapsedMs(a2uiStartedAt),
                    fastRouterHit = fastRouterHit,
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = commandRouterHit,
                    commandRouterMs = commandRouterMs,
                )
            }
            is AgentResponse.EntityChoiceReady -> {
                agentSession.rememberClarification(
                    PendingClarification(
                        entityType = response.entityType,
                        prompt = "Escolha uma opção para continuar.",
                        options = response.options,
                    ),
                )
                val a2uiStartedAt = System.nanoTime()
                AgentA2uiResponse.EntityChoice(
                    intent = intent,
                    entityType = response.entityType,
                    options = response.options,
                    message = entityChoiceMapper.map(response.entityType, response.options),
                    latencyMs = elapsedMs(startedAt),
                    intentLatencyMs = intentLatencyMs,
                    capabilityLatencyMs = capabilityLatencyMs,
                    a2uiLatencyMs = elapsedMs(a2uiStartedAt),
                    fastRouterHit = fastRouterHit,
                    fastRouterMs = fastRouterMs,
                    commandRouterHit = commandRouterHit,
                    commandRouterMs = commandRouterMs,
                )
            }
            is AgentResponse.Unsupported -> AgentA2uiResponse.Unsupported(
                message = response.message,
                latencyMs = elapsedMs(startedAt),
                intentLatencyMs = intentLatencyMs,
                capabilityLatencyMs = capabilityLatencyMs,
                fastRouterHit = fastRouterHit,
                fastRouterMs = fastRouterMs,
                commandRouterHit = commandRouterHit,
                commandRouterMs = commandRouterMs,
            )
        }
    }

    override suspend fun confirm(action: AgentA2uiResponse.ActionPreview): AgentA2uiResponse.ActionCompleted =
        runWithRuntimeSignals { confirmInternal(action) }

    private suspend fun confirmInternal(action: AgentA2uiResponse.ActionPreview): AgentA2uiResponse.ActionCompleted {
        val requiredCapability = action.intent.capability.toTinoCapabilityId()
        val availableCapabilities = agentSession.availableCapabilities()
        if (requiredCapability != null &&
            availableCapabilities.isNotEmpty() &&
            requiredCapability !in availableCapabilities
        ) {
            auditLogger.record(
                AuditEventType.VOICE_STAGE,
                mapOf(
                    "stage" to "CAPABILITY_DISABLED",
                    "capability" to requiredCapability.name,
                    "route" to "confirm",
                ),
            )
            throw IllegalStateException("Esse recurso não está mais ativo para este negócio.")
        }
        agentSession.beginExecuting()
        val capabilityStartedAt = System.nanoTime()
        val calls = pendingCalls.ifEmpty { listOf(action.call) }
        val results = calls.map { boundary.confirm(it, pendingConfirmations[it]) }
        val result = results.last().copy(
            message = results.joinToString(" ") { it.message },
            operationId = results.firstOrNull { it.operationId != null }?.operationId ?: results.last().operationId,
        )
        val capabilityLatencyMs = elapsedMs(capabilityStartedAt)
        val activity = recordActivity(action.intent, result)
        val a2uiStartedAt = System.nanoTime()
        val message = actionMapper.completed(
            result,
            activity?.id,
            activity?.undoState == AgentUndoState.AVAILABLE,
        )
        pendingCalls.clear()
        pendingConfirmations.clear()
        pendingPreview = null
        languageContextMemory.rememberAgentResult(result.message)
        return AgentA2uiResponse.ActionCompleted(
            intent = action.intent,
            result = result,
            message = message,
            activityId = activity?.id,
            latencyMs = capabilityLatencyMs + elapsedMs(a2uiStartedAt),
            intentLatencyMs = action.intentLatencyMs,
            capabilityLatencyMs = capabilityLatencyMs,
            a2uiLatencyMs = elapsedMs(a2uiStartedAt),
        )
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000L

    private fun recordRoutingCompleted(startedAt: Long, route: String, fastPath: Boolean) {
        auditLogger.record(
            AuditEventType.VOICE_STAGE,
            mapOf(
                "stage" to "ROUTING_COMPLETED",
                "duration_ms" to elapsedMs(startedAt).toString(),
                "route" to route,
                "fast_path" to fastPath.toString(),
            ),
        )
    }

    private fun recordActivity(
        intent: AgentIntent,
        result: com.tino.app.domain.voice.ToolExecutionResult,
    ): AgentActivityEntry? {
        val capability = intent.globalToolCall?.name?.let { tool ->
            when (tool) {
                com.tino.app.domain.voice.CommerceToolName.REGISTER_CREDIT_PAYMENT -> TinoCapabilityId.RECEIVE_CREDIT_PAYMENT
                com.tino.app.domain.voice.CommerceToolName.CORRECT_CREDIT_PAYMENT -> TinoCapabilityId.RECEIVE_CREDIT_PAYMENT
                com.tino.app.domain.voice.CommerceToolName.ADD_CREDIT_ITEM,
                com.tino.app.domain.voice.CommerceToolName.REGISTER_CREDIT_SALE -> TinoCapabilityId.ADD_CREDIT_ITEM
                com.tino.app.domain.voice.CommerceToolName.REGISTER_STOCK_RECEIPT -> TinoCapabilityId.REGISTER_STOCK_ENTRY
                com.tino.app.domain.voice.CommerceToolName.CHANGE_PRODUCT_PRICE -> TinoCapabilityId.CHANGE_PRODUCT_PRICE
                com.tino.app.domain.voice.CommerceToolName.SEARCH_CUSTOMER -> TinoCapabilityId.SEARCH_CUSTOMER
                com.tino.app.domain.voice.CommerceToolName.SEARCH_PRODUCT -> TinoCapabilityId.SEARCH_PRODUCT
                com.tino.app.domain.voice.CommerceToolName.CHECK_STOCK -> TinoCapabilityId.READ_STOCK
                com.tino.app.domain.voice.CommerceToolName.GET_CUSTOMER_BALANCE -> TinoCapabilityId.READ_CUSTOMER_BALANCE
                com.tino.app.domain.voice.CommerceToolName.CREATE_CUSTOMER -> TinoCapabilityId.CREATE_CUSTOMER
                else -> null
            }
        } ?: when (intent.capability) {
            AgentCapability.REGISTER_CREDIT_PAYMENT -> TinoCapabilityId.RECEIVE_CREDIT_PAYMENT
            AgentCapability.ADD_CREDIT_ITEM -> TinoCapabilityId.ADD_CREDIT_ITEM
            AgentCapability.CREATE_CUSTOMER -> TinoCapabilityId.CREATE_CUSTOMER
            AgentCapability.UPDATE_PRODUCT_PRICE -> TinoCapabilityId.CHANGE_PRODUCT_PRICE
            AgentCapability.REGISTER_STOCK_ENTRY -> TinoCapabilityId.REGISTER_STOCK_ENTRY
            else -> null
        } ?: return null
        val undo = result.undo?.let { metadata ->
            val compensatingCapability = runCatching {
                TinoCapabilityId.valueOf(metadata.compensatingCapability)
            }.getOrNull() ?: return@let null
            AgentUndoEligibility(
                policy = AgentUndoPolicy.COMPENSATING_OPERATION,
                compensatingCapability = compensatingCapability,
                deadlineEpochMs = metadata.deadlineEpochMs,
            )
        }
        return activityLedger.record(
            capability = capability,
            summary = "${result.title}: ${result.message}",
            source = AgentActivitySource.TEXT,
            operationId = result.operationId,
            undo = undo,
            compensatesActivityId = result.compensatesActivityId,
            summaryData = (result.presentation as? com.tino.app.domain.voice.ToolResultPresentation.Payment)
                ?.let { payment ->
                    AgentActivitySummary.CreditPayment(
                        customerName = payment.customerName,
                        amountCents = payment.amountCents ?: 0L,
                        paymentMethod = payment.methodStorageValue ?: payment.methodLabel,
                    )
                }
                ?: AgentActivitySummary.Generic("${result.title}: ${result.message}"),
        )
    }

    private var pendingPreview: AgentA2uiResponse.ActionPreview? = null
    private val pendingCalls = mutableListOf<com.tino.app.domain.voice.ToolCall>()
    private val pendingConfirmations = mutableMapOf<com.tino.app.domain.voice.ToolCall, MutationConfirmation?>()

    private fun classifyTurn(input: String): ContextTurnClassification {
        val normalized = input.normalizeForContext()
        return when {
            normalized in setOf("sim", "pode", "confirma", "confirmar", "pode lancar", "pode anotar", "pode fazer") &&
                pendingPreview != null -> ContextTurnClassification.CONFIRMATION
            normalized in setOf("sim", "pode", "confirma", "confirmar", "pode lancar", "pode anotar", "pode fazer") ->
                ContextTurnClassification.CONFIRMATION
            normalized in setOf("cancela", "cancelar", "deixa pra la", "nao quero") || normalized.startsWith("cancela ") ->
                ContextTurnClassification.CANCELLATION
            normalized.startsWith("nao") &&
                !normalized.startsWith("nao, ") &&
                setOf("lanca", "lancar", "registra", "registrar", "anota", "anotar", "muda", "mudar", "quero")
                    .any { normalized.contains(it) } -> ContextTurnClassification.CANCELLATION
            normalized.startsWith("nao") -> ContextTurnClassification.CORRECTION
            pendingPreview != null && (
                normalized.startsWith("mais ") ||
                    normalized.startsWith("e mais ") ||
                    normalized.startsWith("nao, ")
                ) -> ContextTurnClassification.CONTINUATION
            else -> ContextTurnClassification.NEW_INTENT
        }
    }

    private fun isDraftTotalQuestion(input: String): Boolean = input.normalizeForContext() in setOf(
        "quanto ficou",
        "quanto ficou a conta",
        "qual ficou o saldo",
    )

    private fun String.normalizeForContext(): String = java.text.Normalizer
        .normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("[^a-z0-9, ]+".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    private fun rememberPendingPreview(preview: AgentA2uiResponse.ActionPreview) {
        val sameDraft = pendingPreview != null &&
            pendingPreview?.intent?.capability == preview.intent.capability &&
            preview.intent.capability == AgentCapability.ADD_CREDIT_ITEM &&
            pendingCalls.firstOrNull()?.arguments?.get("customer") == preview.call.arguments["customer"]
        if (!sameDraft) {
            pendingCalls.clear()
            pendingConfirmations.clear()
        }
        if (!pendingCalls.any { it == preview.call }) pendingCalls += preview.call
        pendingConfirmations[preview.call] = preview.preview.preparedMutation?.confirmation
        pendingPreview = preview
        agentSession.markPreviewReady(
            PendingAgentAction(
                capability = when (preview.intent.capability) {
                    AgentCapability.ADD_CREDIT_ITEM -> TinoCapabilityId.ADD_CREDIT_ITEM
                    AgentCapability.REGISTER_CREDIT_PAYMENT -> TinoCapabilityId.RECEIVE_CREDIT_PAYMENT
                    AgentCapability.CREATE_CUSTOMER -> TinoCapabilityId.CREATE_CUSTOMER
                    AgentCapability.UPDATE_PRODUCT_PRICE -> TinoCapabilityId.CHANGE_PRODUCT_PRICE
                    AgentCapability.REGISTER_STOCK_ENTRY -> TinoCapabilityId.REGISTER_STOCK_ENTRY
                    else -> TinoCapabilityId.NAVIGATE
                },
                summary = preview.preview.detail,
                requiresConfirmation = true,
                collectedSlots = preview.call.arguments,
                draftItems = pendingCalls.map { call ->
                    PendingDraftItem(
                        customer = call.arguments["customer"],
                        product = call.arguments["product"],
                        quantity = call.arguments["quantity"],
                        amount = call.arguments["amount_cents"],
                    )
                },
                stage = PendingActionStage.PREVIEW_READY,
            ),
        )
    }

    private fun mergeDraftPreview(
        preview: com.tino.app.domain.voice.ToolPreview,
        call: com.tino.app.domain.voice.ToolCall,
    ): com.tino.app.domain.voice.ToolPreview {
        val previous = pendingPreview
        val sameDraft = previous != null &&
            previous.intent.capability == AgentCapability.ADD_CREDIT_ITEM &&
            call.arguments["customer"] == pendingCalls.firstOrNull()?.arguments?.get("customer")
        if (!sameDraft || pendingCalls.isEmpty()) return preview
        val details = (pendingCalls.mapNotNull { pendingCall ->
            when (pendingCall) {
                call -> null
                else -> "${pendingCall.arguments["quantity"] ?: "1"} × ${pendingCall.arguments["product"].orEmpty()}"
            }
        } + "${call.arguments["quantity"] ?: "1"} × ${call.arguments["product"].orEmpty()}").joinToString("\\n")
        return preview.copy(detail = details)
    }

    private fun applyCorrection(interpretation: IntentInterpretation): AgentIntent? {
        val previous = pendingPreview?.intent ?: return null
        val correction = interpretation.correction ?: return null
        val slot = when (correction.field) {
            com.tino.app.domain.language.LanguageCorrectionField.QUANTITY -> "quantity"
            com.tino.app.domain.language.LanguageCorrectionField.PAYMENT_METHOD -> "payment_method"
            com.tino.app.domain.language.LanguageCorrectionField.CUSTOMER -> "customer"
            com.tino.app.domain.language.LanguageCorrectionField.PRODUCT -> "product"
            com.tino.app.domain.language.LanguageCorrectionField.AMOUNT -> "amount_cents"
        }
        val value = when (correction.field) {
            com.tino.app.domain.language.LanguageCorrectionField.QUANTITY -> interpretation.quantity?.wholeUnits?.toString()
            com.tino.app.domain.language.LanguageCorrectionField.PAYMENT_METHOD -> interpretation.paymentMethod?.storageValue
            com.tino.app.domain.language.LanguageCorrectionField.CUSTOMER,
            com.tino.app.domain.language.LanguageCorrectionField.PRODUCT -> correction.value
            com.tino.app.domain.language.LanguageCorrectionField.AMOUNT -> interpretation.amountCents?.toString()
        }
        if (!value.isNullOrBlank()) {
            when (val patch = interruptCorrectionRuntime.apply(
                InteractionPatch(
                    updates = mapOf(slot to value),
                    expectedStateVersion = agentSession.snapshot.value.stateVersion,
                ),
            )) {
                is InteractionPatchResult.Applied -> auditLogger.record(
                    AuditEventType.VOICE_STAGE,
                    mapOf(
                        "stage" to "CORRECTION_PATCH_APPLIED",
                        "patch_status" to "APPLIED",
                        "changed_slots" to patch.changedSlots.sorted().joinToString(","),
                        "invalidated_slots" to patch.invalidatedSlots.sorted().joinToString(","),
                        "state_version" to patch.stateVersion.toString(),
                    ),
                )
                is InteractionPatchResult.Rejected -> {
                    auditLogger.record(
                        AuditEventType.VOICE_STAGE,
                        mapOf(
                            "stage" to "CORRECTION_PATCH_REJECTED",
                            "patch_status" to "REJECTED",
                            "patch_rejection" to patch.reason.name,
                            "state_version" to patch.stateVersion.toString(),
                        ),
                    )
                    return null
                }
                InteractionPatchResult.NoPendingAction -> return null
            }
        }
        return when (correction.field) {
            com.tino.app.domain.language.LanguageCorrectionField.QUANTITY -> previous.copy(quantity = interpretation.quantity?.wholeUnits)
            com.tino.app.domain.language.LanguageCorrectionField.PAYMENT_METHOD -> previous.copy(creditPaymentMethod = interpretation.paymentMethod)
            com.tino.app.domain.language.LanguageCorrectionField.CUSTOMER -> previous.copy(customerRef = correction.value)
            com.tino.app.domain.language.LanguageCorrectionField.PRODUCT -> previous.copy(productRef = correction.value)
            com.tino.app.domain.language.LanguageCorrectionField.AMOUNT -> previous.copy(amountCents = interpretation.amountCents)
        }.copy(globalToolCall = null)
    }

    private suspend fun buildPostExecutionCorrection(
        interpretation: IntentInterpretation,
    ): AgentIntent? {
        activityLedger.awaitPersistence()
        val activity = activityLedger.latestSuccessfulMutation() ?: return null
        val payment = activity.summaryData as? AgentActivitySummary.CreditPayment ?: return null
        if (activity.capability != TinoCapabilityId.RECEIVE_CREDIT_PAYMENT) return null
        val originalOperationId = activity.operationId ?: return null
        val correctedAmount = when (interpretation.correction?.field) {
            com.tino.app.domain.language.LanguageCorrectionField.AMOUNT -> interpretation.amountCents
            else -> payment.amountCents
        } ?: return null
        val correctedMethod = when (interpretation.correction?.field) {
            com.tino.app.domain.language.LanguageCorrectionField.PAYMENT_METHOD -> interpretation.paymentMethod
            else -> interpretation.paymentMethod ?: com.tino.app.domain.commerce.PaymentMethod.entries
                .firstOrNull { it.storageValue.equals(payment.paymentMethod, ignoreCase = true) }
        } ?: return null
        val call = com.tino.app.domain.voice.ToolCall(
            name = com.tino.app.domain.voice.CommerceToolName.CORRECT_CREDIT_PAYMENT,
            arguments = mapOf(
                "original_operation_id" to originalOperationId,
                "original_activity_id" to activity.id,
                "amount_cents" to correctedAmount.toString(),
                "payment_method" to correctedMethod.storageValue,
                "operation_id" to com.tino.app.core.common.UuidV7.new(),
            ),
        )
        val correctionIntent = AgentIntent(
            schemaVersion = AgentIntentSchema.VERSION,
            capability = AgentCapability.REGISTER_CREDIT_PAYMENT,
            period = AgentIntentPeriod.TODAY,
            customerRef = payment.customerName,
            amountCents = correctedAmount,
            creditPaymentMethod = correctedMethod,
            globalToolCall = call,
        )
        return correctionIntent
    }
}

interface AgenticTextQueryPort {
    suspend fun ask(input: String): AgentA2uiResponse

    suspend fun askCapability(capability: AgentCapability): AgentA2uiResponse

    /** Executes a capability while preserving the entity that originated the action. */
    suspend fun askCapability(capability: AgentCapability, subjectId: String?): AgentA2uiResponse =
        askCapability(capability)

    suspend fun selectEntityChoice(
        choice: AgentA2uiResponse.EntityChoice,
        selectedLabel: String,
    ): AgentA2uiResponse

    suspend fun confirm(action: AgentA2uiResponse.ActionPreview): AgentA2uiResponse.ActionCompleted
}
