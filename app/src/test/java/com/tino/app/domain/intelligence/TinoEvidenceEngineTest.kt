package com.tino.app.domain.intelligence

import com.tino.app.domain.agent.AgentCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TinoEvidenceEngineTest {
    @Test
    fun stockScreenNamesTheProductInsteadOfRepeatingTheLowStockCount() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(
                    TinoEvidenceProduct("cafe", "Café Maratá", 2),
                    TinoEvidenceProduct("acucar", "Açúcar", 40),
                ),
                recommendations = listOf(
                    Recommendation(
                        id = "r1",
                        type = RecommendationType.REPLENISHMENT,
                        productId = "cafe",
                        message = "Café Maratá pode acabar",
                        confidence = 0.7,
                        evidence = RecommendationEvidence(
                            stockQuantity = 2,
                            unitsSoldLast30Days = 12,
                            rule = "stock_below_thirty_day_demand",
                        ),
                    ),
                ),
            ),
        )
        assertTrue(thoughts.isNotEmpty())
        assertEquals("Café Maratá", thoughts.first().title)
        assertTrue(thoughts.first().body.contains("2"))
        assertEquals(ThoughtClaimKind.FORECAST, thoughts.first().claimKind)
        assertFalse(thoughts.any { it.body.contains("1 produto") })
    }

    @Test
    fun staysSilentWhenNothingIsRelevant() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(TinoEvidenceProduct("leite", "Leite", 18)),
            ),
        )
        assertTrue(thoughts.isEmpty())
    }

    @Test
    fun cadernetaDoesNotRepeatTheVisibleOpenCount() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "CreditList",
                customers = listOf(
                    TinoEvidenceCustomer("maria", "Maria Lina", 7_485),
                    TinoEvidenceCustomer("chico", "Chico Filó", 6_935),
                ),
                todayReceivedCents = 0,
            ),
        )
        assertTrue(thoughts.isNotEmpty())
        assertFalse(thoughts.any { it.body.contains("2 clientes") })
        assertTrue(thoughts.any { it.title == "Maria Lina" })
        assertEquals(ThoughtClaimKind.FACT, thoughts.first().claimKind)
    }

    @Test
    fun homeNamesTheStockoutInsteadOfRepeatingTheLowStockCount() {
        val rec = Recommendation(
            id = "r1",
            type = RecommendationType.STOCKOUT,
            productId = "cafe",
            message = "Café Maratá está sem estoque.",
            confidence = 0.99,
            evidence = RecommendationEvidence(
                stockQuantity = 0,
                unitsSoldLast30Days = 4,
                rule = "stock_zero_with_recent_sales",
            ),
        )
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Home",
                products = listOf(TinoEvidenceProduct("cafe", "Café Maratá", 0)),
                recommendations = listOf(rec),
            ),
        )
        assertTrue(thoughts.any { it.subjectId == "cafe" })
        assertFalse(thoughts.any { it.body.contains("1 produto") })
    }

    @Test
    fun stockoutWithSalesHistoryStaysAFactAndOpensReplenishment() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(TinoEvidenceProduct("oleo", "Óleo", 0)),
                recommendations = listOf(
                    Recommendation(
                        id = "r1",
                        type = RecommendationType.STOCKOUT,
                        productId = "oleo",
                        message = "Óleo está sem estoque.",
                        confidence = 0.99,
                        evidence = RecommendationEvidence(
                            stockQuantity = 0,
                            unitsSoldLast30Days = 5,
                            rule = "stock_zero_with_recent_sales",
                        ),
                    ),
                ),
            ),
        )
        assertEquals(1, thoughts.size)
        assertEquals(ThoughtClaimKind.FACT, thoughts.single().claimKind)
        assertEquals(AgentCapability.REPLENISHMENT_QUERY, thoughts.single().capability)
        assertTrue(thoughts.single().body.contains("5"))
    }

    @Test
    fun neverShowsMoreThanThreeThoughts() {
        val products = (1..8).map { index ->
            TinoEvidenceProduct("p$index", "Produto $index", 0)
        }
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(screen = "Products", products = products),
        )
        assertEquals(TinoEvidenceEngine.MAX_VISIBLE, thoughts.size)
    }

    @Test
    fun uniquePixAmountIsASuspectNotAConfirmedPayment() {
        val match = TinoPaymentMatcher.match(
            pixCents = 18_000,
            debtors = listOf(
                TinoEvidenceCustomer("joao", "João", 18_000),
                TinoEvidenceCustomer("maria", "Maria", 9_000),
            ),
        )
        val unique = match as PaymentMatchResult.UniqueSuspect
        assertEquals("joao", unique.candidate.customerId)
        assertTrue(unique.candidate.confidence < TinoPaymentMatcher.KNOW_THRESHOLD)
        assertFalse(TinoPaymentMatcher.knows(unique.candidate.confidence))
    }

    @Test
    fun twoEqualDebtsMakePixMatchingAmbiguous() {
        val match = TinoPaymentMatcher.match(
            pixCents = 18_000,
            debtors = listOf(
                TinoEvidenceCustomer("joao", "João", 18_000),
                TinoEvidenceCustomer("carlos", "Carlos", 18_000),
            ),
        )
        val ambiguous = match as PaymentMatchResult.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "CreditList",
                customers = listOf(
                    TinoEvidenceCustomer("joao", "João", 18_000),
                    TinoEvidenceCustomer("carlos", "Carlos", 18_000),
                ),
                todayPixCents = 18_000,
            ),
        )
        assertTrue(thoughts.any { it.uncertainty == ThoughtUncertainty.AMBIGUOUS })
        assertTrue(thoughts.any { it.body.contains("João") && it.body.contains("Carlos") })
        assertFalse(thoughts.any { it.body.contains("João pagou") })
    }

    @Test
    fun stockoutForecastUsesDaysAndStaysAnEstimate() {
        val forecast = TinoStockoutForecast.estimate(stockQuantity = 2, soldLast30Days = 12)
        assertEquals(5, forecast?.days)
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(TinoEvidenceProduct("cafe", "Café Maratá", 2)),
                recommendations = listOf(
                    Recommendation(
                        id = "r1",
                        type = RecommendationType.REPLENISHMENT,
                        productId = "cafe",
                        message = "Café Maratá pode acabar",
                        confidence = 0.7,
                        evidence = RecommendationEvidence(
                            stockQuantity = 2,
                            unitsSoldLast30Days = 12,
                            rule = "stock_below_thirty_day_demand",
                        ),
                    ),
                ),
            ),
        )
        assertEquals(ThoughtClaimKind.FORECAST, thoughts.first().claimKind)
        assertEquals(ThoughtUncertainty.SUSPECT, thoughts.first().uncertainty)
        assertTrue(thoughts.first().body.contains("5"))
        assertTrue(thoughts.first().why?.contains("previsão") == true)
    }

    @Test
    fun insufficientSalesHistoryDoesNotInventAForecast() {
        assertEquals(null, TinoStockoutForecast.estimate(stockQuantity = 6, soldLast30Days = 1))
    }

    @Test
    fun seasonalDemandIsAForecastWithExplicitUncertainty() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                weekday = java.time.DayOfWeek.THURSDAY,
                products = listOf(
                    TinoEvidenceProduct(
                        id = "soda",
                        name = "Refrigerante",
                        stockQuantity = 6,
                        unitsSoldByWeekday = mapOf(java.time.DayOfWeek.FRIDAY to 5),
                    ),
                ),
            ),
        )
        assertTrue(thoughts.any { it.type == ThoughtType.PREDICTION })
        assertTrue(thoughts.single { it.type == ThoughtType.PREDICTION }.body.contains("Amanhã"))
        assertEquals(ThoughtUncertainty.SUSPECT, thoughts.single { it.type == ThoughtType.PREDICTION }.uncertainty)
    }

    @Test
    fun acceleratedSalesAreAnAnomalyWithoutInventingTheCause() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(
                    TinoEvidenceProduct(
                        id = "coffee",
                        name = "Café",
                        stockQuantity = 8,
                        unitsSoldPrevious30Days = 4,
                        unitsSoldLast30Days = 8,
                    ),
                ),
            ),
        )
        val anomaly = thoughts.single { it.type == ThoughtType.ANOMALY }
        assertEquals(ThoughtClaimKind.INFERENCE, anomaly.claimKind)
        assertTrue(anomaly.why?.contains("causa") == true)
    }

    @Test
    fun analysisKeepsEvidenceLinkedToTheRankedInsight() {
        val analysis = TinoEvidenceEngine.analyze(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(TinoEvidenceProduct("coffee", "Café", 0)),
                memories = listOf(TinoEvidenceMemory("delivery", "recebe às quartas", 0.9)),
            ),
        )
        assertTrue(analysis.insights.isNotEmpty())
        assertEquals(analysis.insights.size, analysis.evidence.size)
        assertTrue(analysis.insights.all { insight -> insight.evidenceIds.all { id -> analysis.evidence.any { it.id == id } } })
        assertTrue(analysis.evidence.any { it.source == TinoEvidenceSource.BUSINESS_MEMORY })
    }

    @Test
    fun evidenceRetainsObservedValuesInsteadOfOnlyRenderedThoughtText() {
        val analysis = TinoEvidenceEngine.analyze(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(
                    TinoEvidenceProduct(
                        id = "coffee",
                        name = "Café",
                        stockQuantity = 2,
                        unitsSoldLast30Days = 12,
                        lastMovementAtEpochMs = 7_000L,
                    ),
                ),
            ),
        )

        val evidence = analysis.evidence.single()
        assertEquals(TinoEvidenceSource.ROOM, evidence.source)
        assertEquals("coffee", evidence.facts["product_id"])
        assertEquals("2", evidence.facts["stock_quantity"])
        assertEquals("12", evidence.facts["units_sold_last_30_days"])
        assertEquals("7000", evidence.facts["last_movement_at_epoch_ms"])
        assertEquals("FACT", evidence.facts["claim"])
    }

    @Test
    fun evidenceRetainsRecommendationRuleAndFeatureValuesWhenItSupportsAnInsight() {
        val analysis = TinoEvidenceEngine.analyze(
            TinoEvidenceSnapshot(
                screen = "Home",
                products = listOf(TinoEvidenceProduct("coffee", "Café", 0)),
                recommendations = listOf(
                    Recommendation(
                        id = "recommendation-1",
                        type = RecommendationType.STOCKOUT,
                        productId = "coffee",
                        message = "Café está sem estoque.",
                        confidence = 0.99,
                        evidence = RecommendationEvidence(
                            stockQuantity = 0,
                            unitsSoldLast30Days = 12,
                            rule = "stock_zero_with_recent_sales",
                        ),
                    ),
                ),
            ),
        )

        val evidence = analysis.evidence.single()
        assertEquals("recommendation-1", evidence.facts["recommendation_id"])
        assertEquals("stock_zero_with_recent_sales", evidence.facts["recommendation_rule"])
        assertEquals("12", evidence.facts["recommendation_units_sold_last_30_days"])
        assertEquals("inventory-features-v1", evidence.facts["recommendation_feature_version"])
    }

    @Test
    fun rankedInsightCarriesContextImpactHorizonAndGenerationMetadata() {
        val now = 123_456L
        val dates = (1..7).associate { day -> LocalDate.of(2026, 8, day) to 4 } +
            (LocalDate.of(2026, 8, 8) to 5)
        val analysis = TinoEvidenceEngine.analyze(
            TinoEvidenceSnapshot(
                screen = "Products",
                entityProductId = "coffee",
                nowEpochMs = now,
                products = listOf(
                    TinoEvidenceProduct(
                        id = "coffee",
                        name = "Café",
                        stockQuantity = 2,
                        unitsSoldByDate = dates,
                    ),
                ),
            ),
        )

        val insight = analysis.insights.single { it.id == "demand-forecast:coffee" }
        assertTrue(insight.contextRelevance >= 90)
        assertTrue(insight.businessImpact >= 80)
        assertEquals("7 dias", insight.timeHorizon)
        assertEquals(now, insight.generatedAtEpochMs)
        val evidence = analysis.evidence.single { it.id in insight.evidenceIds }
        assertEquals("7 dias", evidence.facts["time_horizon"])
        assertEquals("STATISTICAL", evidence.facts["forecast_method"])
    }

    @Test
    fun analysisKeepsEvidenceForCandidatesEvenWhenTheAttentionSurfaceShowsOnlyThree() {
        val analysis = TinoEvidenceEngine.analyze(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = (1..4).map { TinoEvidenceProduct("p$it", "Produto $it", 0) },
            ),
        )

        assertEquals(TinoEvidenceEngine.MAX_VISIBLE, analysis.visibleThoughts.size)
        assertEquals(4, analysis.evidence.size)
        assertTrue(analysis.insights.all { insight -> insight.evidenceIds.all { id -> analysis.evidence.any { it.id == id } } })
    }

    @Test
    fun paymentDelayIsPresentedAsAHistoryBasedInference() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "CreditList",
                customers = listOf(
                    TinoEvidenceCustomer(
                        id = "maria",
                        name = "Maria",
                        balanceCents = 2_000,
                        averagePaymentDelayDays = 12.0,
                    ),
                ),
            ),
        )

        val delay = thoughts.single { it.id == "payment-delay:maria" }
        assertEquals(ThoughtClaimKind.INFERENCE, delay.claimKind)
        assertEquals(ThoughtUncertainty.SUSPECT, delay.uncertainty)
        assertTrue(delay.body.contains("12 dias"))
        assertTrue(delay.why?.contains("não garante") == true)
    }

    @Test
    fun financialGrowthIsAPositiveSignalInsteadOfAnAnomaly() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Insights",
                currentWeekReceivedCents = 1_500L,
                previousWeekReceivedCents = 1_000L,
            ),
        )

        assertEquals(ThoughtType.POSITIVE_SIGNAL, thoughts.single().type)
        assertEquals(ThoughtClaimKind.INFERENCE, thoughts.single().claimKind)
    }

    @Test
    fun supplierHistoryExplainsRiskAndPriceChangeWithoutInventingCause() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Home",
                products = listOf(
                    TinoEvidenceProduct(
                        id = "coffee",
                        name = "Café",
                        stockQuantity = 2,
                        supplierName = "Distribuidora Norte",
                        supplierPurchaseCountLast90Days = 3,
                        lastPurchaseCostCents = 1_200,
                        previousPurchaseCostCents = 1_000,
                    ),
                ),
            ),
        )

        val price = thoughts.first { it.id == "supplier-price:coffee" }
        assertEquals(ThoughtClaimKind.INFERENCE, price.claimKind)
        assertTrue(price.why?.contains("não explica") == true)
        assertTrue(thoughts.any { it.id == "supplier-recurring:coffee" })
    }

    @Test
    fun supplierDeliveryDateProducesAgroundedLateSignalAndDeliveryPattern() {
        val now = 100L * 24L * 60L * 60L * 1_000L
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Home",
                nowEpochMs = now,
                products = listOf(
                    TinoEvidenceProduct(
                        id = "coffee",
                        name = "Café",
                        stockQuantity = 12,
                        supplierName = "Distribuidora Norte",
                        supplierExpectedDeliveryAtEpochMs = now - 1_000L,
                        supplierLateDeliveryCount = 2,
                        supplierOnTimeDeliveryCount = 1,
                    ),
                ),
            ),
        )

        val late = thoughts.single { it.id == "supplier-delivery-late:coffee" }
        assertEquals(ThoughtClaimKind.FACT, late.claimKind)
        assertEquals(ThoughtUncertainty.KNOW, late.uncertainty)
        assertTrue(late.body.contains("ainda não foi registrada"))
        assertTrue(thoughts.any { it.id == "supplier-delivery-pattern:coffee" })
    }

    @Test
    fun customerPurchaseRhythmIsAvailableEvenWhenTheBalanceIsZero() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Customers",
                nowEpochMs = 100L * 24L * 60L * 60L * 1_000L,
                customers = listOf(
                    TinoEvidenceCustomer(
                        id = "c1",
                        name = "Maria",
                        balanceCents = 0,
                        purchaseCountLast90Days = 4,
                        averagePurchaseIntervalDays = 10.0,
                    ),
                ),
            ),
        )

        val rhythm = thoughts.single { it.id == "customer-rhythm:c1" }
        assertEquals(ThoughtClaimKind.INFERENCE, rhythm.claimKind)
        assertEquals(ThoughtUncertainty.SUSPECT, rhythm.uncertainty)
    }

    @Test
    fun creditGrowthAndUpcomingPromiseAreSeparateGroundedSignals() {
        val now = 10L * 24L * 60L * 60L * 1_000L
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "CreditList",
                nowEpochMs = now,
                customers = listOf(
                    TinoEvidenceCustomer(
                        id = "c1",
                        name = "João",
                        balanceCents = 2_000,
                        promisedPaymentAtEpochMs = now + 3L * 24L * 60L * 60L * 1_000L,
                        balanceChangeLast30Cents = 800,
                    ),
                ),
            ),
        )

        assertTrue(thoughts.any { it.id == "balance-growth:c1" && it.claimKind == ThoughtClaimKind.FACT })
        assertTrue(thoughts.any { it.id == "promise-upcoming:c1" && it.claimKind == ThoughtClaimKind.FACT })
    }

    @Test
    fun financialProjectionIsMarkedAsForecast() {
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Insights",
                currentWeekReceivedCents = 300,
                currentWeekElapsedDays = 3,
            ),
        )

        val projection = thoughts.single { it.id == "financial-projection:Insights" }
        assertEquals(ThoughtClaimKind.FORECAST, projection.claimKind)
        assertEquals(ThoughtUncertainty.SUSPECT, projection.uncertainty)
    }

    @Test
    fun statisticalSalesAnomalyRequiresHistoryAndExplainsBaseline() {
        val dates = (1..7).associate { LocalDate.of(2026, 8, it) to 1 } +
            (LocalDate.of(2026, 8, 8) to 5)
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(
                    TinoEvidenceProduct(
                        id = "cafe",
                        name = "Café",
                        stockQuantity = 30,
                        unitsSoldByDate = dates,
                    ),
                ),
            ),
        )

        val anomaly = thoughts.single { it.id.startsWith("statistical-sales-anomaly:") }
        assertEquals(ThoughtType.ANOMALY, anomaly.type)
        assertTrue(anomaly.body.contains("5 unidades"))
        assertTrue(anomaly.why.orEmpty().contains("7 dias anteriores"))
        assertEquals(ThoughtUncertainty.SUSPECT, anomaly.uncertainty)
    }

    @Test
    fun statisticalSalesAnomalyStaysSilentWithInsufficientHistory() {
        val dates = (1..6).associate { LocalDate.of(2026, 8, it) to 1 } +
            (LocalDate.of(2026, 8, 7) to 5)
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(
                    TinoEvidenceProduct(
                        id = "cafe",
                        name = "Café",
                        stockQuantity = 30,
                        unitsSoldByDate = dates,
                    ),
                ),
            ),
        )

        assertTrue(thoughts.none { it.id.startsWith("statistical-sales-anomaly:") })
    }

    @Test
    fun weekdayForecastRequiresRepeatedObservationsAndExplainsTheComparison() {
        val dates = buildMap {
            (0..6).forEach { offset ->
                put(LocalDate.of(2026, 8, 4).plusWeeks(offset.toLong()), 6)
                put(LocalDate.of(2026, 8, 3).plusWeeks(offset.toLong()), 2)
            }
        }
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                weekday = java.time.DayOfWeek.MONDAY,
                products = listOf(
                    TinoEvidenceProduct(
                        id = "cafe",
                        name = "Café",
                        stockQuantity = 8,
                        unitsSoldByDate = dates,
                    ),
                ),
            ),
        )

        val seasonal = thoughts.single { it.id == "seasonal:cafe:TUESDAY" }
        assertEquals(ThoughtClaimKind.FORECAST, seasonal.claimKind)
        assertTrue(seasonal.body.contains("6 unidades"))
        assertTrue(seasonal.why.orEmpty().contains("7 ocorrências"))
        assertEquals(ThoughtUncertainty.SUSPECT, seasonal.uncertainty)
    }

    @Test
    fun weekdayForecastStaysSilentWithOnlyOneMatchingDay() {
        val dates = (0..6).associate { offset ->
            LocalDate.of(2026, 8, 3).plusDays(offset.toLong()) to 2
        }
        val pattern = TinoWeekdaySalesStatistics.detect(dates, java.time.DayOfWeek.TUESDAY)

        assertEquals(null, pattern)
    }

    @Test
    fun demandForecastShowsAnUncertaintyRangeWhenStockCannotCoverTheLowerBound() {
        val dates = (1..7).associate { day -> LocalDate.of(2026, 8, day) to 4 } +
            (LocalDate.of(2026, 8, 8) to 5)
        val thoughts = TinoEvidenceEngine.thoughtsFor(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(
                    TinoEvidenceProduct(
                        id = "cafe",
                        name = "Café",
                        stockQuantity = 2,
                        unitsSoldByDate = dates,
                    ),
                ),
            ),
        )

        val forecast = thoughts.single { it.id == "demand-forecast:cafe" }
        assertEquals(ThoughtClaimKind.FORECAST, forecast.claimKind)
        assertTrue(forecast.body.contains("próximos 7 dias"))
        assertTrue(forecast.why.orEmpty().contains("faixa observada"))
        assertEquals(ThoughtUncertainty.SUSPECT, forecast.uncertainty)
        assertEquals(DemandForecastMethod.STATISTICAL, forecast.forecastMethod)
    }

    @Test
    fun regressionDemandModelUsesObservedHistoryAndExposesItsMethod() {
        val dates = (0 until 14).associate { offset ->
            LocalDate.of(2026, 8, 1).plusDays(offset.toLong()) to (2 + offset)
        }

        val forecast = TinoDemandRegressionModel.forecast(dates, horizonDays = 7)

        assertEquals(DemandForecastMethod.LINEAR_REGRESSION, forecast?.method)
        assertTrue((forecast?.expectedUnits ?: 0) > 0)
        assertTrue((forecast?.upperUnits ?: 0) >= (forecast?.lowerUnits ?: 0))
        assertEquals(14, forecast?.observationDays)
    }

    @Test
    fun regressionDemandModelDoesNotFillMissingDatesWithInventedZeros() {
        val dates = (0 until 13).associate { offset ->
            LocalDate.of(2026, 8, 1).plusDays((offset * 2L)) to 4
        }

        assertEquals(null, TinoDemandRegressionModel.forecast(dates, horizonDays = 7))
    }

    @Test
    fun regressionModelNeedsASeparateValidationWindowBeforePromotion() {
        val insufficientForValidation = (0 until 16).associate { offset ->
            LocalDate.of(2026, 8, 1).plusDays(offset.toLong()) to (2 + offset)
        }

        assertEquals(null, TinoDemandModelValidator.evaluate(insufficientForValidation))
    }

    @Test
    fun regressionModelPassesTheGateOnAStableOutOfSampleTrend() {
        val observations = (0 until 20).associate { offset ->
            LocalDate.of(2026, 8, 1).plusDays(offset.toLong()) to (2 + offset)
        }

        val evaluation = TinoDemandModelValidator.evaluate(observations)

        assertTrue((evaluation?.validationWindows ?: 0) >= 3)
        assertTrue((evaluation?.meanAbsolutePercentageError ?: 1.0) <= 0.50)
        assertTrue(evaluation?.passesGate == true)
    }

    @Test
    fun evidenceEngineUsesValidatedRegressionAndRecordsItsMethod() {
        val observations = (0 until 20).associate { offset ->
            LocalDate.of(2026, 8, 1).plusDays(offset.toLong()) to (2 + offset)
        }
        val evaluation = TinoDemandModelValidator.evaluate(observations)
        val analysis = TinoEvidenceEngine.analyze(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(
                    TinoEvidenceProduct(
                        id = "coffee",
                        name = "Café",
                        stockQuantity = 2,
                        unitsSoldByDate = observations,
                        demandModelEvaluation = evaluation,
                    ),
                ),
            ),
        )

        val forecast = analysis.insights.single { it.id == "demand-forecast:coffee" }
        assertEquals("7 dias", forecast.timeHorizon)
        assertEquals(DemandForecastMethod.LINEAR_REGRESSION.name, forecast.evidenceIds
            .mapNotNull { id -> analysis.evidence.firstOrNull { it.id == id }?.facts?.get("forecast_method") }
            .single())
        val evidence = analysis.evidence.single { it.id == forecast.evidenceIds.single() }
        assertEquals(evaluation?.meanAbsolutePercentageError.toString(), evidence.facts["demand_mape"])
        assertEquals("true", evidence.facts["demand_model_passes_gate"])
    }

    @Test
    fun changedObservationCreatesANewEvidenceVersionInsteadOfOverwritingHistory() {
        val withoutSales = TinoEvidenceEngine.analyze(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(TinoEvidenceProduct("p1", "Café", 0)),
            ),
        )
        val withSales = TinoEvidenceEngine.analyze(
            TinoEvidenceSnapshot(
                screen = "Products",
                products = listOf(TinoEvidenceProduct("p1", "Café", 0)),
                recommendations = listOf(
                    Recommendation(
                        id = "r1",
                        type = RecommendationType.STOCKOUT,
                        productId = "p1",
                        message = "Café está sem estoque.",
                        confidence = 0.99,
                        evidence = RecommendationEvidence(0, 4, "stock_zero_with_recent_sales"),
                    ),
                ),
            ),
        )

        assertTrue(withoutSales.insights.single().evidenceIds.single() != withSales.insights.single().evidenceIds.single())
    }
}
