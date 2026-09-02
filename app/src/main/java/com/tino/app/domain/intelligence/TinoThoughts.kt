package com.tino.app.domain.intelligence

import com.tino.app.domain.agent.AgentCapability
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

enum class ThoughtType {
    OBSERVATION,
    ATTENTION,
    OPPORTUNITY,
    ANOMALY,
    REMINDER,
    PATTERN,
    PREDICTION,
    POSITIVE_SIGNAL,
    SUGGESTION,
    QUESTION,
}

enum class ThoughtClaimKind { FACT, INFERENCE, FORECAST }

data class TinoEvidenceProduct(
    val id: String,
    val name: String,
    val stockQuantity: Int,
    val unitsSoldPrevious30Days: Int? = null,
    val unitsSoldLast30Days: Int? = null,
    val unitsSoldByWeekday: Map<DayOfWeek, Int> = emptyMap(),
    val lastMovementAtEpochMs: Long? = null,
    val supplierId: String? = null,
    val supplierName: String? = null,
    val supplierPurchaseCountLast90Days: Int = 0,
    val lastPurchaseAtEpochMs: Long? = null,
    val lastPurchaseCostCents: Long? = null,
    val previousPurchaseCostCents: Long? = null,
    val unitsSoldByDate: Map<LocalDate, Int> = emptyMap(),
    val supplierExpectedDeliveryAtEpochMs: Long? = null,
    val supplierLastReceivedAtEpochMs: Long? = null,
    val supplierLateDeliveryCount: Int = 0,
    val supplierOnTimeDeliveryCount: Int = 0,
    val demandModelEvaluation: DemandModelEvaluation? = null,
)

data class TinoEvidenceCustomer(
    val id: String,
    val name: String,
    val balanceCents: Long,
    val lastActivityAtEpochMs: Long? = null,
    val promisedPaymentAtEpochMs: Long? = null,
    val averagePaymentDelayDays: Double? = null,
    val balanceChangeLast30Cents: Long = 0L,
    val purchaseCountLast90Days: Int = 0,
    val purchaseCountPrevious90Days: Int = 0,
    val averagePurchaseIntervalDays: Double? = null,
    val lastPaymentAtEpochMs: Long? = null,
)

data class TinoEvidenceMemory(
    val key: String,
    val value: String,
    val confidence: Double,
)

data class TinoEvidenceSnapshot(
    val screen: String,
    val products: List<TinoEvidenceProduct> = emptyList(),
    val customers: List<TinoEvidenceCustomer> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val todayReceivedCents: Long = 0,
    val todayPixCents: Long = 0,
    val todaySales: Int = 0,
    val weekday: DayOfWeek = DayOfWeek.MONDAY,
    val entityProductId: String? = null,
    val entityCustomerId: String? = null,
    val nowEpochMs: Long = System.currentTimeMillis(),
    val currentWeekReceivedCents: Long? = null,
    val previousWeekReceivedCents: Long? = null,
    val currentWeekElapsedDays: Int? = null,
    val receivedByMethod: Map<String, Long> = emptyMap(),
    val memories: List<TinoEvidenceMemory> = emptyList(),
)

data class TinoThought(
    val id: String,
    val type: ThoughtType,
    val claimKind: ThoughtClaimKind,
    val subjectId: String,
    val actionSubjectId: String? = null,
    val title: String,
    val body: String,
    val why: String? = null,
    val relevance: Int,
    val uncertainty: ThoughtUncertainty = ThoughtUncertainty.KNOW,
    val confidence: Double = 1.0,
    val capability: AgentCapability? = null,
    val actionLabel: String? = "Ver",
    val evidenceIds: List<String> = emptyList(),
    val forecastMethod: DemandForecastMethod? = null,
    val timeHorizon: String? = null,
)

enum class TinoEvidenceType {
    OBSERVATION,
    TEMPORAL,
    RELATION,
    ANOMALY,
    PREDICTION,
    MEMORY,
}

enum class TinoEvidenceSource { ROOM, DERIVED, BUSINESS_MEMORY }

data class TinoBusinessEvidence(
    val id: String,
    val type: TinoEvidenceType,
    val subjectId: String?,
    val facts: Map<String, String>,
    val source: TinoEvidenceSource,
    val confidence: Double,
    val occurredAtEpochMs: Long?,
    val detectedAtEpochMs: Long,
)

data class TinoInsight(
    val id: String,
    val type: ThoughtType,
    val subjectId: String? = null,
    val title: String,
    val explanation: String,
    val evidenceIds: List<String>,
    val confidence: Double,
    val relevance: Int,
    val urgency: Int,
    val novelty: Int,
    val actions: List<AgentCapability>,
    val contextRelevance: Int = 50,
    val businessImpact: Int = 50,
    val timeHorizon: String? = null,
    val generatedAtEpochMs: Long = 0L,
)

data class TinoIntelligenceAnalysis(
    val evidence: List<TinoBusinessEvidence>,
    val insights: List<TinoInsight>,
    val visibleThoughts: List<TinoThought> = emptyList(),
    val candidateInsights: List<TinoInsight> = emptyList(),
)

/**
 * Deterministic evidence ranker. It never invents money, dates or patterns:
 * only facts in the snapshot, plus forecasts tagged when sales history exists.
 */
object TinoEvidenceEngine {
    const val MAX_VISIBLE = 3
    const val MIN_RELEVANCE = 60

    fun thoughtsFor(snapshot: TinoEvidenceSnapshot): List<TinoThought> {
        return rank(candidateThoughts(snapshot), snapshot)
    }

    private fun candidateThoughts(snapshot: TinoEvidenceSnapshot): List<TinoThought> {
        val family = familyOf(snapshot.screen)
        val pendingRecs = snapshot.recommendations.filter { it.decision == RecommendationDecision.PENDING }
        return when (family) {
            ScreenFamily.STOCK -> inventoryThoughts(snapshot, pendingRecs)
            ScreenFamily.CREDIT, ScreenFamily.CUSTOMERS -> creditThoughts(snapshot)
            ScreenFamily.HOME -> homeThoughts(snapshot, pendingRecs)
            ScreenFamily.FINANCE -> financeThoughts(snapshot)
            ScreenFamily.OTHER -> emptyList()
        } + relationalThoughts(snapshot, family) + temporalThoughts(snapshot, family) + memoryThoughts(snapshot)
    }

    private fun rank(
        candidates: List<TinoThought>,
        snapshot: TinoEvidenceSnapshot,
    ): List<TinoThought> = candidates
            .filter { it.relevance >= MIN_RELEVANCE }
            .distinctBy { it.subjectId + it.type.name }
            .sortedWith(
                compareByDescending<TinoThought> { rankingScore(it, snapshot) }
                    .thenByDescending { it.relevance },
            )
            .take(MAX_VISIBLE)

    /** Relevance leads; urgency, novelty and confidence break ties deliberately. */
    private fun rankingScore(thought: TinoThought, snapshot: TinoEvidenceSnapshot): Double =
        thought.relevance * 0.45 +
            urgencyFor(thought) * 0.15 +
            noveltyFor(thought) * 0.1 +
            thought.confidence.coerceIn(0.0, 1.0) * 100.0 * 0.1 +
            contextRelevanceFor(thought, snapshot) * 0.1 +
            businessImpactFor(thought) * 0.1

    /**
     * Structured boundary for the Intelligence System. The UI may consume
     * thoughts, while telemetry and future attention surfaces retain the
     * evidence that justified each one.
     */
    fun analyze(snapshot: TinoEvidenceSnapshot): TinoIntelligenceAnalysis {
        val candidates = candidateThoughts(snapshot)
        val thoughts = rank(candidates, snapshot)
        val evidence = candidates.map { thought ->
            TinoBusinessEvidence(
                id = evidenceIdFor(thought),
                type = thought.evidenceType(),
                subjectId = thought.subjectId,
                facts = evidenceFactsFor(snapshot, thought),
                source = evidenceSourceFor(thought),
                confidence = thought.confidence,
                occurredAtEpochMs = occurredAtFor(snapshot, thought),
                detectedAtEpochMs = snapshot.nowEpochMs,
            )
        }
        val evidenceByThought = candidates.zip(evidence).associate { (thought, item) -> thought.id to item }
        fun insightFor(thought: TinoThought): TinoInsight = TinoInsight(
            id = thought.id,
            type = thought.type,
            subjectId = thought.subjectId,
            title = thought.title,
            explanation = thought.why ?: thought.body,
            evidenceIds = listOfNotNull(evidenceByThought[thought.id]?.id),
            confidence = thought.confidence,
            relevance = thought.relevance,
            urgency = urgencyFor(thought),
            novelty = noveltyFor(thought),
            actions = thought.capability?.let(::listOf).orEmpty(),
            contextRelevance = contextRelevanceFor(thought, snapshot),
            businessImpact = businessImpactFor(thought),
            timeHorizon = thought.timeHorizon,
            generatedAtEpochMs = snapshot.nowEpochMs,
        )
        return TinoIntelligenceAnalysis(
            evidence = evidence,
            insights = thoughts.map(::insightFor),
            visibleThoughts = thoughts,
            candidateInsights = candidates.map(::insightFor),
        )
    }

    private fun evidenceIdFor(thought: TinoThought): String =
        "evidence:${thought.id}:${Integer.toHexString((thought.body + "|" + thought.why.orEmpty()).hashCode())}"

    /**
     * Keeps provenance useful to a caller that does not have the original
     * snapshot. The visible copy remains human language, while this map keeps
     * the observed values that led to the candidate. Derived conclusions also
     * retain the claim and explanation without pretending they are raw facts.
     */
    private fun evidenceFactsFor(
        snapshot: TinoEvidenceSnapshot,
        thought: TinoThought,
    ): Map<String, String> = buildMap {
        put("claim", thought.claimKind.name)
        thought.subjectId?.let { put("subject_id", it) }
        put("title", thought.title)
        put("body", thought.body)
        thought.why?.let { put("explanation", it) }
        thought.forecastMethod?.let { put("forecast_method", it.name) }
        thought.timeHorizon?.let { put("time_horizon", it) }

        snapshot.products.firstOrNull { it.id == thought.subjectId }?.let { product ->
            put("product_id", product.id)
            put("product_name", product.name)
            put("stock_quantity", product.stockQuantity.toString())
            product.unitsSoldPrevious30Days?.let { put("units_sold_previous_30_days", it.toString()) }
            product.unitsSoldLast30Days?.let { put("units_sold_last_30_days", it.toString()) }
            product.lastMovementAtEpochMs?.let { put("last_movement_at_epoch_ms", it.toString()) }
            product.supplierName?.let { put("supplier_name", it) }
            product.supplierPurchaseCountLast90Days
                .takeIf { it > 0 }
                ?.let { put("supplier_purchase_count_90_days", it.toString()) }
            product.lastPurchaseCostCents?.let { put("last_purchase_cost_cents", it.toString()) }
            product.previousPurchaseCostCents?.let { put("previous_purchase_cost_cents", it.toString()) }
            product.supplierExpectedDeliveryAtEpochMs?.let { put("supplier_expected_delivery_at_epoch_ms", it.toString()) }
            product.supplierLastReceivedAtEpochMs?.let { put("supplier_last_received_at_epoch_ms", it.toString()) }
            put("supplier_late_delivery_count", product.supplierLateDeliveryCount.toString())
            put("supplier_on_time_delivery_count", product.supplierOnTimeDeliveryCount.toString())
            product.demandModelEvaluation?.let { evaluation ->
                put("demand_validation_windows", evaluation.validationWindows.toString())
                put("demand_mae", evaluation.meanAbsoluteError.toString())
                put("demand_mape", evaluation.meanAbsolutePercentageError.toString())
                put("demand_interval_coverage", evaluation.intervalCoverage.toString())
                put("demand_model_passes_gate", evaluation.passesGate.toString())
            }
        }

        snapshot.recommendations.firstOrNull { it.productId == thought.subjectId }?.let { recommendation ->
            put("recommendation_id", recommendation.id)
            put("recommendation_type", recommendation.type.name)
            put("recommendation_model_version", recommendation.modelVersion)
            recommendation.evidence?.let { recommendationEvidence ->
                put("recommendation_rule", recommendationEvidence.rule)
                put("recommendation_stock_quantity", recommendationEvidence.stockQuantity.toString())
                put("recommendation_units_sold_last_30_days", recommendationEvidence.unitsSoldLast30Days.toString())
                put("recommendation_feature_quality", recommendationEvidence.quality.name)
                put("recommendation_feature_version", recommendationEvidence.featureVersion)
            }
        }

        snapshot.customers.firstOrNull { it.id == thought.subjectId }?.let { customer ->
            put("customer_id", customer.id)
            put("customer_name", customer.name)
            put("balance_cents", customer.balanceCents.toString())
            customer.lastActivityAtEpochMs?.let { put("last_activity_at_epoch_ms", it.toString()) }
            customer.promisedPaymentAtEpochMs?.let { put("promised_payment_at_epoch_ms", it.toString()) }
            customer.averagePaymentDelayDays?.let { put("average_payment_delay_days", it.toString()) }
            put("balance_change_last_30_cents", customer.balanceChangeLast30Cents.toString())
            put("purchase_count_90_days", customer.purchaseCountLast90Days.toString())
            put("purchase_count_previous_90_days", customer.purchaseCountPrevious90Days.toString())
            customer.averagePurchaseIntervalDays?.let { put("average_purchase_interval_days", it.toString()) }
            customer.lastPaymentAtEpochMs?.let { put("last_payment_at_epoch_ms", it.toString()) }
        }

        if (thought.subjectId == "financial-week" || thought.id.startsWith("financial-")) {
            snapshot.currentWeekReceivedCents?.let { put("current_week_received_cents", it.toString()) }
            snapshot.previousWeekReceivedCents?.let { put("previous_week_received_cents", it.toString()) }
            snapshot.currentWeekElapsedDays?.let { put("current_week_elapsed_days", it.toString()) }
            if (snapshot.receivedByMethod.isNotEmpty()) {
                put(
                    "received_by_method_cents",
                    snapshot.receivedByMethod.entries.joinToString(",") { (method, cents) -> "$method=$cents" },
                )
            }
        }

        if (thought.subjectId.startsWith("pix:") || thought.id.startsWith("pix-")) {
            put("pix_cents", snapshot.todayPixCents.toString())
            put("open_debtor_count", snapshot.customers.count { it.balanceCents > 0 }.toString())
        }

        if (thought.id.startsWith("memory:")) {
            snapshot.memories.firstOrNull { it.key == thought.subjectId.removePrefix("memory:") }?.let { memory ->
                put("memory_key", memory.key)
                put("memory_value", memory.value)
                put("memory_confidence", memory.confidence.toString())
            }
        }
    }

    private fun evidenceSourceFor(thought: TinoThought): TinoEvidenceSource = when {
        thought.id.startsWith("memory:") -> TinoEvidenceSource.BUSINESS_MEMORY
        thought.claimKind == ThoughtClaimKind.INFERENCE || thought.claimKind == ThoughtClaimKind.FORECAST ->
            TinoEvidenceSource.DERIVED
        else -> TinoEvidenceSource.ROOM
    }

    private fun contextRelevanceFor(
        thought: TinoThought,
        snapshot: TinoEvidenceSnapshot,
    ): Int {
        if (thought.subjectId == snapshot.entityProductId || thought.subjectId == snapshot.entityCustomerId) {
            return 100
        }
        return when (familyOf(snapshot.screen)) {
            ScreenFamily.HOME -> 75
            ScreenFamily.STOCK -> if (snapshot.products.any { it.id == thought.subjectId }) 90 else 45
            ScreenFamily.CREDIT, ScreenFamily.CUSTOMERS -> if (snapshot.customers.any { it.id == thought.subjectId }) 90 else 45
            ScreenFamily.FINANCE -> if (thought.subjectId == "financial-week" || thought.subjectId == "today-received") 90 else 45
            ScreenFamily.OTHER -> 35
        }
    }

    private fun businessImpactFor(thought: TinoThought): Int = when (thought.type) {
        ThoughtType.ATTENTION -> 100
        ThoughtType.ANOMALY -> 90
        ThoughtType.QUESTION -> 88
        ThoughtType.PREDICTION -> 80
        ThoughtType.OPPORTUNITY -> 76
        ThoughtType.REMINDER -> 70
        ThoughtType.PATTERN -> 62
        ThoughtType.POSITIVE_SIGNAL -> 58
        ThoughtType.SUGGESTION -> 55
        ThoughtType.OBSERVATION -> 50
    }

    private fun occurredAtFor(snapshot: TinoEvidenceSnapshot, thought: TinoThought): Long? =
        snapshot.products.firstOrNull { it.id == thought.subjectId }?.lastMovementAtEpochMs
            ?: snapshot.customers.firstOrNull { it.id == thought.subjectId }?.lastActivityAtEpochMs
            ?: snapshot.nowEpochMs

    private fun homeThoughts(
        snapshot: TinoEvidenceSnapshot,
        pendingRecs: List<Recommendation>,
    ): List<TinoThought> {
        return inventoryThoughts(snapshot, pendingRecs) +
            creditThoughts(snapshot) +
            financeThoughts(snapshot)
    }

    private fun inventoryThoughts(
        snapshot: TinoEvidenceSnapshot,
        pendingRecs: List<Recommendation>,
    ): List<TinoThought> {
        val recsByProduct = pendingRecs.associateBy { it.productId }
        val focusId = snapshot.entityProductId
        return snapshot.products
            .filter { focusId == null || it.id == focusId }
            .mapNotNull { product ->
                val rec = recsByProduct[product.id]
                val sold = rec?.evidence?.unitsSoldLast30Days
                val baseThought = when {
                    product.stockQuantity <= 0 && sold != null && sold > 0 -> TinoThought(
                        id = "stockout:${product.id}",
                        type = ThoughtType.ATTENTION,
                        claimKind = ThoughtClaimKind.FACT,
                        subjectId = product.id,
                        title = product.name,
                        body = "Está sem estoque e teve $sold ${units(sold)} nos últimos 30 dias.",
                        why = "Pode estar perdendo venda agora.",
                        relevance = 94,
                        capability = AgentCapability.REPLENISHMENT_QUERY,
                    )
                    product.stockQuantity <= 0 -> TinoThought(
                        id = "empty:${product.id}",
                        type = ThoughtType.ATTENTION,
                        claimKind = ThoughtClaimKind.FACT,
                        subjectId = product.id,
                        title = product.name,
                        body = "Está sem estoque.",
                        why = "Se houver procura, a ruptura já aconteceu.",
                        relevance = 78,
                        capability = AgentCapability.REPLENISHMENT_QUERY,
                    )
                    product.stockQuantity in 1..6 && sold != null && sold > 0 -> {
                        val forecast = TinoStockoutForecast.estimate(product.stockQuantity, sold)
                        val nearWeekend = snapshot.weekday.isNearWeekend() && product.stockQuantity <= 6
                        when {
                            forecast != null -> TinoThought(
                                id = "forecast:${product.id}",
                                type = ThoughtType.ATTENTION,
                                claimKind = ThoughtClaimKind.FORECAST,
                                subjectId = product.id,
                                title = product.name,
                                body = "Restam ${product.stockQuantity} ${units(product.stockQuantity)}. Nesse ritmo, pode acabar em ${forecast.days} ${if (forecast.days == 1) "dia" else "dias"}.",
                                why = "Saíram $sold ${units(sold)} nos últimos 30 dias. Isso é uma previsão, não uma certeza.",
                                relevance = if (forecast.days <= 2) 92 else 84,
                                uncertainty = ThoughtUncertainty.SUSPECT,
                                confidence = forecast.confidence,
                                capability = AgentCapability.REPLENISHMENT_QUERY,
                                timeHorizon = "${forecast.days} ${if (forecast.days == 1) "dia" else "dias"}",
                            )
                            nearWeekend -> TinoThought(
                                id = "weekend:${product.id}",
                                type = ThoughtType.SUGGESTION,
                                claimKind = ThoughtClaimKind.INFERENCE,
                                subjectId = product.id,
                                title = product.name,
                                body = "Restam ${product.stockQuantity} ${units(product.stockQuantity)} e o fim de semana está perto.",
                                why = "Não há certeza de pico; só o estoque baixo perto do fim de semana.",
                                relevance = 68,
                                uncertainty = ThoughtUncertainty.SUSPECT,
                                confidence = 0.55,
                                capability = AgentCapability.REPLENISHMENT_QUERY,
                            )
                            rec?.type == RecommendationType.REPLENISHMENT -> TinoThought(
                                id = "forecast:${product.id}",
                                type = ThoughtType.ATTENTION,
                                claimKind = ThoughtClaimKind.FORECAST,
                                subjectId = product.id,
                                title = product.name,
                                body = "Restam ${product.stockQuantity} ${units(product.stockQuantity)}. Pelo ritmo recente, pode acabar em breve.",
                                why = "Isso é uma previsão com base nas saídas dos últimos 30 dias.",
                                relevance = 86,
                                uncertainty = ThoughtUncertainty.SUSPECT,
                                confidence = 0.65,
                                capability = AgentCapability.REPLENISHMENT_QUERY,
                                timeHorizon = "em breve",
                            )
                            else -> TinoThought(
                                id = "low:${product.id}",
                                type = ThoughtType.ATTENTION,
                                claimKind = ThoughtClaimKind.FACT,
                                subjectId = product.id,
                                title = product.name,
                                body = "Restam apenas ${product.stockQuantity} ${units(product.stockQuantity)}.",
                                why = "O nome do produto não aparece no resumo da tela.",
                                relevance = 76,
                                capability = AgentCapability.REPLENISHMENT_QUERY,
                            )
                        }
                    }
                    product.stockQuantity in 1..2 -> TinoThought(
                        id = "low:${product.id}",
                        type = ThoughtType.ATTENTION,
                        claimKind = ThoughtClaimKind.FACT,
                        subjectId = product.id,
                        title = product.name,
                        body = "Restam apenas ${product.stockQuantity} ${units(product.stockQuantity)}.",
                        why = "O nome do produto não aparece no resumo da tela.",
                        relevance = 76,
                        capability = AgentCapability.REPLENISHMENT_QUERY,
                    )
                    rec?.type == RecommendationType.SLOW_MOVING -> TinoThought(
                        id = "slow:${product.id}",
                        type = ThoughtType.PATTERN,
                        claimKind = ThoughtClaimKind.INFERENCE,
                        subjectId = product.id,
                        title = product.name,
                        body = "O estoque está alto e as saídas recentes foram baixas.",
                        why = "Pode não valer repor agora.",
                        relevance = 64,
                        capability = AgentCapability.LIST_PRODUCTS,
                    )
                    else -> null
                }
                val supplierThoughts = supplierThoughts(product, snapshot.nowEpochMs)
                listOfNotNull(baseThought) + supplierThoughts
            }
            .flatten()
    }

    private fun supplierThoughts(product: TinoEvidenceProduct, nowEpochMs: Long): List<TinoThought> {
        val thoughts = mutableListOf<TinoThought>()
        if (product.supplierName != null && product.stockQuantity in 0..6) {
            thoughts += TinoThought(
                id = "supplier-risk:${product.id}",
                type = ThoughtType.SUGGESTION,
                claimKind = ThoughtClaimKind.FACT,
                subjectId = product.id,
                actionSubjectId = product.supplierId,
                title = product.name,
                body = "O fornecedor registrado é ${product.supplierName}.",
                why = "Pode ajudar a agir sobre este risco de estoque sem procurar o fornecedor de novo.",
                relevance = 63,
                capability = AgentCapability.LIST_SUPPLIERS,
                actionLabel = "Ver fornecedor",
            )
        }
        if (product.supplierName != null && product.supplierPurchaseCountLast90Days >= 2) {
            thoughts += TinoThought(
                id = "supplier-recurring:${product.id}",
                type = ThoughtType.PATTERN,
                claimKind = ThoughtClaimKind.FACT,
                subjectId = product.id,
                title = product.supplierName,
                body = "Há ${product.supplierPurchaseCountLast90Days} compras deste produto com esse fornecedor nos últimos 90 dias.",
                why = "O histórico de compras ajuda a localizar uma reposição recorrente; não é uma ordem de compra.",
                relevance = 62,
                capability = AgentCapability.LIST_SUPPLIERS,
                actionLabel = "Ver fornecedor",
            )
        }
        val latest = product.lastPurchaseCostCents
        val previous = product.previousPurchaseCostCents
        if (latest != null && previous != null && previous > 0L) {
            val variation = (latest - previous).toDouble() / previous
            if (kotlin.math.abs(variation) >= 0.1) {
                val direction = if (variation > 0) "subiu" else "caiu"
                thoughts += TinoThought(
                    id = "supplier-price:${product.id}",
                    type = ThoughtType.ANOMALY,
                    claimKind = ThoughtClaimKind.INFERENCE,
                    subjectId = product.id,
                    title = product.name,
                    body = "O custo de compra $direction ${"%.0f".format(Locale.US, kotlin.math.abs(variation) * 100)}% desde a compra anterior.",
                    why = "A comparação usa compras registradas; não explica sozinha a causa da variação.",
                    relevance = 73,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = 0.8,
                    capability = AgentCapability.LIST_SUPPLIERS,
                    actionLabel = "Ver fornecedor",
                )
            }
        }
        product.supplierExpectedDeliveryAtEpochMs?.let { expectedAt ->
            val expectedDate = java.time.Instant.ofEpochMilli(expectedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            if (expectedAt <= nowEpochMs) {
                thoughts += TinoThought(
                    id = "supplier-delivery-late:${product.id}",
                    type = ThoughtType.ANOMALY,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = product.id,
                    title = product.name,
                    body = "A entrega de ${product.supplierName ?: "um fornecedor"} estava prevista para ${formatDate(expectedDate)} e ainda não foi registrada.",
                    why = "Existe um pedido de compra em aberto com data prevista, mas nenhum recebimento associado foi registrado.",
                    relevance = 88,
                    uncertainty = ThoughtUncertainty.KNOW,
                    confidence = 1.0,
                    capability = AgentCapability.LIST_SUPPLIERS,
                    actionLabel = "Ver fornecedor",
                )
            } else if (expectedAt <= nowEpochMs + 7L * 24L * 60L * 60L * 1_000L) {
                thoughts += TinoThought(
                    id = "supplier-delivery-upcoming:${product.id}",
                    type = ThoughtType.REMINDER,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = product.id,
                    title = product.name,
                    body = "A entrega de ${product.supplierName ?: "um fornecedor"} está prevista para ${formatDate(expectedDate)}.",
                    why = "A data vem do pedido de compra registrado; a entrega ainda não foi confirmada.",
                    relevance = 70,
                    uncertainty = ThoughtUncertainty.KNOW,
                    confidence = 1.0,
                    capability = AgentCapability.LIST_SUPPLIERS,
                    actionLabel = "Ver fornecedor",
                )
            }
        }
        val completedDeliveries = product.supplierLateDeliveryCount + product.supplierOnTimeDeliveryCount
        if (completedDeliveries >= 3 && product.supplierLateDeliveryCount > 0) {
            thoughts += TinoThought(
                id = "supplier-delivery-pattern:${product.id}",
                type = ThoughtType.PATTERN,
                claimKind = ThoughtClaimKind.INFERENCE,
                subjectId = product.id,
                title = product.supplierName ?: product.name,
                body = "Esse fornecedor entregou ${product.supplierLateDeliveryCount} de ${completedDeliveries} pedidos depois da data prevista.",
                why = "O padrão usa apenas pedidos com data prevista e recebimento registrado; não explica o motivo dos atrasos.",
                relevance = 74,
                uncertainty = ThoughtUncertainty.SUSPECT,
                confidence = 0.78,
                capability = AgentCapability.LIST_SUPPLIERS,
                actionLabel = "Ver fornecedor",
            )
        }
        return thoughts
    }

    private fun creditThoughts(snapshot: TinoEvidenceSnapshot): List<TinoThought> {
        val focusId = snapshot.entityCustomerId
        val debtors = snapshot.customers
            .filter { it.balanceCents > 0 }
            .filter { focusId == null || it.id == focusId }
            .sortedByDescending { it.balanceCents }
        val thoughts = mutableListOf<TinoThought>()
        if (debtors.isNotEmpty()) {
            val total = debtors.sumOf { it.balanceCents }
            val top = debtors.first()
            if (total > 0 && top.balanceCents * 2 >= total && debtors.size > 1) {
                thoughts += TinoThought(
                    id = "concentration:${top.id}",
                    type = ThoughtType.PATTERN,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = top.id,
                    title = top.name,
                    body = "Concentra ${money(top.balanceCents)} dos ${money(total)} em aberto.",
                    why = "É quem mais move o resultado se pagar.",
                    relevance = 82,
                    capability = AgentCapability.GET_CUSTOMER_BALANCE,
                )
            } else {
                thoughts += TinoThought(
                    id = "balance:${top.id}",
                    type = ThoughtType.OPPORTUNITY,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = top.id,
                    title = top.name,
                    body = "Tem ${money(top.balanceCents)} em aberto.",
                    why = "Uma cobrança aqui muda o caixa.",
                    relevance = 74,
                    capability = AgentCapability.GET_CUSTOMER_BALANCE,
                )
            }
            if (snapshot.todayReceivedCents == 0L && snapshot.todayPixCents == 0L && debtors.size >= 2 && focusId == null) {
                thoughts += TinoThought(
                    id = "no-payment-today",
                    type = ThoughtType.REMINDER,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = "today-payments",
                    title = "Caderneta",
                    body = "Nenhum pagamento foi registrado hoje.",
                    why = "Há valores em aberto que ainda podem entrar.",
                    relevance = 66,
                    capability = AgentCapability.LIST_RECEIVABLES,
                )
            }
            debtors.filter { customer ->
                customer.promisedPaymentAtEpochMs != null &&
                    customer.promisedPaymentAtEpochMs <= snapshot.nowEpochMs
            }.take(1).forEach { customer ->
                thoughts += TinoThought(
                    id = "promise-due:${customer.id}",
                    type = ThoughtType.REMINDER,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = customer.id,
                    title = customer.name,
                    body = "A promessa de pagamento venceu e ainda há ${money(customer.balanceCents)} em aberto.",
                    why = "A data da promessa já passou; isso não confirma que o cliente não pagará.",
                    relevance = 86,
                    capability = AgentCapability.GET_CUSTOMER_BALANCE,
                )
            }
            debtors.filter { it.balanceChangeLast30Cents > 0L }.maxByOrNull { it.balanceChangeLast30Cents }?.let { customer ->
                thoughts += TinoThought(
                    id = "balance-growth:${customer.id}",
                    type = ThoughtType.ANOMALY,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = customer.id,
                    title = customer.name,
                    body = "O saldo aumentou ${money(customer.balanceChangeLast30Cents)} nos últimos 30 dias.",
                    why = "É a diferença entre vendas fiadas e pagamentos registrados no período.",
                    relevance = 79,
                    capability = AgentCapability.GET_CUSTOMER_TIMELINE,
                )
            }
            debtors.filter { customer ->
                val due = customer.promisedPaymentAtEpochMs
                due != null && due > snapshot.nowEpochMs && due - snapshot.nowEpochMs <= 7L * 24L * 60L * 60L * 1_000L
            }.take(1).forEach { customer ->
                val due = customer.promisedPaymentAtEpochMs ?: return@forEach
                val days = ((due - snapshot.nowEpochMs) / (24L * 60L * 60L * 1_000L)).coerceAtLeast(1)
                thoughts += TinoThought(
                    id = "promise-upcoming:${customer.id}",
                    type = ThoughtType.REMINDER,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = customer.id,
                    title = customer.name,
                    body = "Prometeu pagar em até $days ${if (days == 1L) "dia" else "dias"} e ainda há ${money(customer.balanceCents)} em aberto.",
                    why = "A promessa está registrada no histórico; não garante o recebimento.",
                    relevance = 72,
                    capability = AgentCapability.GET_CUSTOMER_BALANCE,
                )
            }
            debtors.filter { customer ->
                val delay = customer.averagePaymentDelayDays
                delay != null && delay >= 7.0
            }.maxByOrNull { it.averagePaymentDelayDays ?: 0.0 }?.let { customer ->
                val delay = customer.averagePaymentDelayDays ?: return@let
                thoughts += TinoThought(
                    id = "payment-delay:${customer.id}",
                    type = ThoughtType.PATTERN,
                    claimKind = ThoughtClaimKind.INFERENCE,
                    subjectId = customer.id,
                    title = customer.name,
                    body = "Nos pagamentos registrados, costuma levar cerca de ${"%.0f".format(Locale.US, delay)} dias para pagar.",
                    why = "É uma média do histórico; não garante o prazo deste pagamento.",
                    relevance = 78,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = 0.74,
                    capability = AgentCapability.GET_CUSTOMER_TIMELINE,
                )
            }
            debtors.filter { customer ->
                customer.lastActivityAtEpochMs != null &&
                    snapshot.nowEpochMs - customer.lastActivityAtEpochMs >= 30L * 24L * 60L * 60L * 1_000L
            }.take(1).forEach { customer ->
                thoughts += TinoThought(
                    id = "inactive-debtor:${customer.id}",
                    type = ThoughtType.REMINDER,
                    claimKind = ThoughtClaimKind.INFERENCE,
                    subjectId = customer.id,
                    title = customer.name,
                    body = "A conta está em aberto e não há movimentação há pelo menos 30 dias.",
                    why = "Vale conferir a linha do tempo antes de decidir uma cobrança.",
                    relevance = 75,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = 0.77,
                    capability = AgentCapability.GET_CUSTOMER_TIMELINE,
                )
            }
        }
        snapshot.customers.filter { customer ->
            (focusId == null || customer.id == focusId) &&
                customer.balanceCents == 0L && customer.purchaseCountLast90Days >= 3 &&
                customer.averagePurchaseIntervalDays != null && customer.averagePurchaseIntervalDays <= 14.0
        }.take(1).forEach { customer ->
            val interval = customer.averagePurchaseIntervalDays ?: return@forEach
            thoughts += TinoThought(
                id = "customer-rhythm:${customer.id}",
                type = ThoughtType.PATTERN,
                claimKind = ThoughtClaimKind.INFERENCE,
                subjectId = customer.id,
                title = customer.name,
                body = "Compra com frequência: em média a cada ${"%.0f".format(Locale.US, interval)} dias.",
                why = "O padrão vem das compras registradas nos últimos 90 dias; não garante a próxima compra.",
                relevance = 67,
                uncertainty = ThoughtUncertainty.SUSPECT,
                confidence = 0.72,
                capability = AgentCapability.GET_CUSTOMER_TIMELINE,
            )
        }
        snapshot.customers.filter { customer ->
            (focusId == null || customer.id == focusId) &&
                customer.purchaseCountLast90Days >= 3 &&
                customer.purchaseCountLast90Days >= customer.purchaseCountPrevious90Days * 2 + 1
        }.take(1).forEach { customer ->
            thoughts += TinoThought(
                id = "customer-change:${customer.id}",
                type = ThoughtType.PATTERN,
                claimKind = ThoughtClaimKind.INFERENCE,
                subjectId = customer.id,
                title = customer.name,
                body = "A frequência de compras aumentou neste período.",
                why = "Foram ${customer.purchaseCountLast90Days} compras nos últimos 90 dias contra ${customer.purchaseCountPrevious90Days} no período anterior.",
                relevance = 65,
                uncertainty = ThoughtUncertainty.SUSPECT,
                confidence = 0.73,
                capability = AgentCapability.GET_CUSTOMER_TIMELINE,
            )
        }
        snapshot.customers.filter { customer ->
            (focusId == null || customer.id == focusId) &&
                customer.balanceCents == 0L && customer.lastPaymentAtEpochMs != null &&
                snapshot.nowEpochMs - customer.lastPaymentAtEpochMs <= 7L * 24L * 60L * 60L * 1_000L
        }.take(1).forEach { customer ->
            thoughts += TinoThought(
                id = "customer-settled:${customer.id}",
                type = ThoughtType.POSITIVE_SIGNAL,
                claimKind = ThoughtClaimKind.FACT,
                subjectId = customer.id,
                title = customer.name,
                body = "A conta está quitada.",
                why = "O saldo atual não tem valor em aberto e houve pagamento recente registrado.",
                relevance = 61,
                capability = AgentCapability.GET_CUSTOMER_BALANCE,
            )
        }
        return thoughts
    }

    private fun relationalThoughts(
        snapshot: TinoEvidenceSnapshot,
        family: ScreenFamily,
    ): List<TinoThought> {
        if (family !in setOf(ScreenFamily.CREDIT, ScreenFamily.CUSTOMERS, ScreenFamily.HOME, ScreenFamily.FINANCE)) {
            return emptyList()
        }
        val debtors = snapshot.customers.filter { it.balanceCents > 0 }
        return when (val match = TinoPaymentMatcher.match(snapshot.todayPixCents, debtors)) {
            PaymentMatchResult.None -> emptyList()
            is PaymentMatchResult.Ambiguous -> listOf(
                TinoThought(
                    id = "pix-ambiguous:${snapshot.todayPixCents}",
                    type = ThoughtType.QUESTION,
                    claimKind = ThoughtClaimKind.INFERENCE,
                    subjectId = "pix:${snapshot.todayPixCents}",
                    title = "Pix ${money(snapshot.todayPixCents)}",
                    body = "Pode ser de ${match.candidates.joinToString(" ou ") { it.customerName }}. Quer identificar?",
                    why = "O valor bate com mais de uma conta em aberto. Isso não é baixa automática.",
                    relevance = 93,
                    uncertainty = ThoughtUncertainty.AMBIGUOUS,
                    confidence = TinoPaymentMatcher.AMBIGUOUS_AMOUNT_CONFIDENCE,
                    capability = AgentCapability.LIST_RECEIVABLES,
                    actionLabel = "Identificar",
                ),
            )
            is PaymentMatchResult.UniqueSuspect -> {
                if (TinoPaymentMatcher.knows(match.candidate.confidence)) emptyList()
                else listOf(
                    TinoThought(
                        id = "pix-suspect:${match.candidate.customerId}",
                        type = ThoughtType.QUESTION,
                        claimKind = ThoughtClaimKind.INFERENCE,
                        subjectId = match.candidate.customerId,
                        title = match.candidate.customerName,
                        body = "Entrou um Pix de ${money(snapshot.todayPixCents)} que pode estar relacionado a esta conta.",
                        why = "Só o valor coincidiu. O TINO não dá baixa sozinho.",
                        relevance = 88,
                        uncertainty = ThoughtUncertainty.SUSPECT,
                        confidence = match.candidate.confidence,
                        capability = AgentCapability.LIST_RECEIVABLES,
                        actionLabel = "Identificar",
                    ),
                )
            }
        }
    }

    private fun financeThoughts(snapshot: TinoEvidenceSnapshot): List<TinoThought> {
        val thoughts = mutableListOf<TinoThought>()
        if (snapshot.todayReceivedCents > 0) thoughts += TinoThought(
            id = "today-in",
            type = ThoughtType.OBSERVATION,
            claimKind = ThoughtClaimKind.FACT,
            subjectId = "today-received",
            title = "Hoje",
            body = "Entraram ${money(snapshot.todayReceivedCents)}.",
            relevance = 62,
            capability = AgentCapability.READ_FINANCIAL_SUMMARY,
        )
        val current = snapshot.currentWeekReceivedCents
        val previous = snapshot.previousWeekReceivedCents
        if (current != null && previous != null && previous > 0L) {
            val variation = ((current - previous).toDouble() / previous) * 100.0
            if (kotlin.math.abs(variation) >= 25.0) {
                val direction = if (variation < 0) "abaixo" else "acima"
                thoughts += TinoThought(
                    id = "financial-anomaly:${snapshot.screen}",
                    type = if (variation < 0) ThoughtType.ANOMALY else ThoughtType.POSITIVE_SIGNAL,
                    claimKind = ThoughtClaimKind.INFERENCE,
                    subjectId = "financial-week",
                    title = "Recebimentos",
                    body = "Esta semana entrou ${money(current)}, ${"%.0f".format(Locale.US, kotlin.math.abs(variation))}% $direction da semana passada.",
                    why = "A diferença é grande o bastante para merecer uma olhada, mas não indica a causa sozinha.",
                    relevance = if (kotlin.math.abs(variation) >= 50.0) 88 else 76,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = 0.82,
                    capability = AgentCapability.READ_FINANCIAL_SUMMARY,
                )
            }
        }
        val elapsedDays = snapshot.currentWeekElapsedDays
        if (current != null && current > 0L && elapsedDays != null && elapsedDays in 2..6) {
            val projected = current * 7L / elapsedDays
            thoughts += TinoThought(
                id = "financial-projection:${snapshot.screen}",
                type = ThoughtType.PREDICTION,
                claimKind = ThoughtClaimKind.FORECAST,
                subjectId = "financial-week",
                title = "Recebimentos",
                body = "Mantido o ritmo atual, a semana pode fechar perto de ${money(projected)}.",
                why = "É uma projeção simples de ${money(current)} em $elapsedDays dias; não é garantia.",
                relevance = 63,
                uncertainty = ThoughtUncertainty.SUSPECT,
                confidence = 0.62,
                capability = AgentCapability.READ_FINANCIAL_SUMMARY,
                timeHorizon = "fim desta semana",
            )
        }
        val methodTotal = snapshot.receivedByMethod.values.sum()
        val dominant = snapshot.receivedByMethod.maxByOrNull { it.value }
        if (dominant != null && methodTotal > 0L && snapshot.receivedByMethod.size > 1 &&
            dominant.value.toDouble() / methodTotal >= 0.8
        ) {
            thoughts += TinoThought(
                id = "payment-method-pattern:${dominant.key}",
                type = ThoughtType.PATTERN,
                claimKind = ThoughtClaimKind.FACT,
                subjectId = "payment-method:${dominant.key}",
                title = dominant.key,
                body = "${dominant.key} concentra ${"%.0f".format(Locale.US, dominant.value * 100.0 / methodTotal)}% dos recebimentos.",
                why = "Esse padrão pode ajudar a decidir como oferecer cobrança.",
                relevance = 64,
                capability = AgentCapability.READ_FINANCIAL_SUMMARY,
            )
        }
        return thoughts
    }

    private fun temporalThoughts(
        snapshot: TinoEvidenceSnapshot,
        family: ScreenFamily,
    ): List<TinoThought> {
        if (family !in setOf(ScreenFamily.STOCK, ScreenFamily.HOME)) return emptyList()
        val tomorrow = snapshot.weekday.plus(1)
        return snapshot.products.flatMap { product ->
            if (snapshot.entityProductId != null && snapshot.entityProductId != product.id) {
                return@flatMap emptyList()
            }
            val thoughts = mutableListOf<TinoThought>()
            val tomorrowSales = product.unitsSoldByWeekday[tomorrow]
            val statisticalWeekday = TinoWeekdaySalesStatistics.detect(product.unitsSoldByDate, tomorrow)
            if (statisticalWeekday != null &&
                product.stockQuantity <= kotlin.math.ceil(statisticalWeekday.averageUnits * 2.0).toInt()
            ) {
                val averageUnits = kotlin.math.round(statisticalWeekday.averageUnits).toInt().coerceAtLeast(1)
                thoughts += TinoThought(
                    id = "seasonal:${product.id}:${tomorrow.name}",
                    type = ThoughtType.PREDICTION,
                    claimKind = ThoughtClaimKind.FORECAST,
                    subjectId = product.id,
                    title = product.name,
                    body = "Amanhã costuma sair cerca de $averageUnits ${units(averageUnits)} e restam ${product.stockQuantity}.",
                    why = "A média de ${formatUnits(statisticalWeekday.averageUnits)} foi observada em ${statisticalWeekday.weekdayObservationDays} ocorrências desse dia, contra ${formatUnits(statisticalWeekday.overallAverageUnits)} nos ${statisticalWeekday.observationDays} dias observados; não é garantia de venda.",
                    relevance = 86,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = statisticalWeekday.confidence,
                    capability = AgentCapability.REPLENISHMENT_QUERY,
                    timeHorizon = "amanhã",
                )
            } else if (tomorrowSales != null && tomorrowSales > 0 && product.stockQuantity <= tomorrowSales * 2) {
                thoughts += TinoThought(
                    id = "seasonal:${product.id}:${tomorrow.name}",
                    type = ThoughtType.PREDICTION,
                    claimKind = ThoughtClaimKind.FORECAST,
                    subjectId = product.id,
                    title = product.name,
                    body = "Amanhã costuma sair cerca de $tomorrowSales ${units(tomorrowSales)} e restam ${product.stockQuantity}.",
                    why = "É baseado no histórico do dia da semana; não é garantia de venda.",
                    relevance = 86,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = 0.68,
                    capability = AgentCapability.REPLENISHMENT_QUERY,
                    timeHorizon = "amanhã",
                )
            }
            val current = product.unitsSoldLast30Days
            val previous = product.unitsSoldPrevious30Days
            if (current != null && previous != null && previous > 0 && current.toDouble() / previous >= 1.5) {
                thoughts += TinoThought(
                    id = "acceleration:${product.id}",
                    type = ThoughtType.ANOMALY,
                    claimKind = ThoughtClaimKind.INFERENCE,
                    subjectId = product.id,
                    title = product.name,
                    body = "As saídas aceleraram: $current ${units(current)} contra $previous no período anterior.",
                    why = "O ritmo aumentou pelo menos 50%; ainda não é possível afirmar a causa.",
                    relevance = 84,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = 0.76,
                    capability = AgentCapability.GET_PRODUCT_STOCK,
                )
            }
            val lastMovement = product.lastMovementAtEpochMs
            if (lastMovement != null && product.stockQuantity > 0 &&
                snapshot.nowEpochMs - lastMovement >= 30L * 24L * 60L * 60L * 1_000L && current == 0
            ) {
                thoughts += TinoThought(
                    id = "stale:${product.id}",
                    type = ThoughtType.PATTERN,
                    claimKind = ThoughtClaimKind.INFERENCE,
                    subjectId = product.id,
                    title = product.name,
                    body = "Está há pelo menos 30 dias sem movimentação e ainda tem ${product.stockQuantity} ${units(product.stockQuantity)}.",
                    why = "Pode ser estoque parado; confirme antes de decidir uma compra.",
                    relevance = 72,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = 0.72,
                    capability = AgentCapability.LIST_PRODUCTS,
                )
            }
            (product.demandModelEvaluation
                ?.takeIf { it.passesGate }
                ?.let { TinoDemandRegressionModel.forecast(product.unitsSoldByDate, horizonDays = 7) }
                ?: TinoDemandForecastStatistics.forecast(product.unitsSoldByDate, horizonDays = 7))
                ?.takeIf { it.lowerUnits > product.stockQuantity }
                ?.let { demand ->
                    val sourceDescription = when (demand.method) {
                        DemandForecastMethod.LINEAR_REGRESSION -> "um modelo local ajustado às vendas observadas"
                        DemandForecastMethod.STATISTICAL -> "a média e a dispersão dos dias observados"
                    }
                    val rangeDescription = when (demand.method) {
                        DemandForecastMethod.LINEAR_REGRESSION -> "a faixa estimada"
                        DemandForecastMethod.STATISTICAL -> "a faixa observada"
                    }
                    thoughts += TinoThought(
                        id = "demand-forecast:${product.id}",
                        type = ThoughtType.PREDICTION,
                        claimKind = ThoughtClaimKind.FORECAST,
                        subjectId = product.id,
                        title = product.name,
                        body = "Nos próximos 7 dias, podem sair cerca de ${demand.expectedUnits} ${units(demand.expectedUnits)}; hoje há ${product.stockQuantity}.",
                        why = "A estimativa usa $sourceDescription em ${demand.observationDays} dias de venda: $rangeDescription vai de ${demand.lowerUnits} a ${demand.upperUnits} ${units(demand.upperUnits)}. É uma previsão, não uma garantia.",
                        relevance = 83,
                        uncertainty = ThoughtUncertainty.SUSPECT,
                        confidence = demand.confidence,
                        capability = AgentCapability.REPLENISHMENT_QUERY,
                        forecastMethod = demand.method,
                        timeHorizon = "7 dias",
                    )
                }
            TinoDailySalesStatistics.detect(product.unitsSoldByDate)?.let { anomaly ->
                thoughts += TinoThought(
                    id = "statistical-sales-anomaly:${product.id}:${anomaly.date}",
                    type = ThoughtType.ANOMALY,
                    claimKind = ThoughtClaimKind.INFERENCE,
                    subjectId = product.id,
                    title = product.name,
                    body = "Saíram ${anomaly.currentUnits} ${units(anomaly.currentUnits)} em ${formatDate(anomaly.date)}, acima do padrão de ${formatUnits(anomaly.baselineMean)} por dia.",
                    why = "A média dos ${anomaly.observationDays} dias anteriores foi ${formatUnits(anomaly.baselineMean)}; o desvio observado foi ${formatUnits(anomaly.baselineStandardDeviation)}. Isso indica mudança, não a causa.",
                    relevance = 82,
                    uncertainty = ThoughtUncertainty.SUSPECT,
                    confidence = anomaly.confidence,
                    capability = AgentCapability.GET_PRODUCT_STOCK,
                )
            }
            thoughts
        }
    }

    private fun memoryThoughts(snapshot: TinoEvidenceSnapshot): List<TinoThought> =
        snapshot.memories
            .filter { it.confidence >= 0.7 && it.value.isNotBlank() }
            .map { memory ->
                TinoThought(
                    id = "memory:${memory.key}",
                    type = ThoughtType.OBSERVATION,
                    claimKind = ThoughtClaimKind.FACT,
                    subjectId = memory.key,
                    title = "Aprendi sobre o seu comércio",
                    body = "${memory.key}: ${memory.value}.",
                    why = "Esse contexto veio de uma confirmação anterior e não substitui os dados atuais.",
                    relevance = 61,
                    confidence = memory.confidence,
                )
            }

    private fun TinoThought.evidenceType(): TinoEvidenceType = when {
        id.startsWith("memory:") -> TinoEvidenceType.MEMORY
        type == ThoughtType.ANOMALY -> TinoEvidenceType.ANOMALY
        claimKind == ThoughtClaimKind.FORECAST -> TinoEvidenceType.PREDICTION
        type == ThoughtType.QUESTION -> TinoEvidenceType.RELATION
        type == ThoughtType.PATTERN -> TinoEvidenceType.TEMPORAL
        else -> TinoEvidenceType.OBSERVATION
    }

    private fun urgencyFor(thought: TinoThought): Int = when (thought.type) {
        ThoughtType.ATTENTION, ThoughtType.ANOMALY -> 80
        ThoughtType.QUESTION -> 70
        ThoughtType.PREDICTION, ThoughtType.REMINDER -> 60
        else -> 35
    }

    private fun noveltyFor(thought: TinoThought): Int = when (thought.type) {
        ThoughtType.ANOMALY, ThoughtType.QUESTION, ThoughtType.PATTERN -> 75
        else -> 50
    }

    private enum class ScreenFamily { HOME, STOCK, CREDIT, CUSTOMERS, FINANCE, OTHER }

    private fun familyOf(screen: String): ScreenFamily = when (screen) {
        "Home" -> ScreenFamily.HOME
        "Products", "ProductDetail", "StockEntry", "AdjustStock" -> ScreenFamily.STOCK
        "CreditList", "CustomerAccount", "ReceivePayment", "SelectCustomer", "ConfirmCredit" -> ScreenFamily.CREDIT
        "Customers", "CustomerDetail" -> ScreenFamily.CUSTOMERS
        "More", "DailySummary", "Insights" -> ScreenFamily.FINANCE
        else -> ScreenFamily.OTHER
    }

    private fun units(count: Int): String = if (count == 1) "unidade" else "unidades"

    private fun formatUnits(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.US, value)

    private fun formatDate(value: LocalDate): String =
        "${value.dayOfMonth.toString().padStart(2, '0')}/${value.monthValue.toString().padStart(2, '0')}"

    private fun money(cents: Long): String =
        "R$ " + "%,.2f".format(Locale("pt", "BR"), cents / 100.0)
}
