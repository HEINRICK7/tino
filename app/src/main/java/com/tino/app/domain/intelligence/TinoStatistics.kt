package com.tino.app.domain.intelligence

import java.time.LocalDate
import java.time.DayOfWeek
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.math.sqrt

/** A small, explainable statistical detector for a product's daily sales. */
data class DailySalesAnomaly(
    val date: LocalDate,
    val currentUnits: Int,
    val baselineMean: Double,
    val baselineStandardDeviation: Double,
    val zScore: Double,
    val observationDays: Int,
    val confidence: Double,
)

data class WeekdaySalesPattern(
    val weekday: DayOfWeek,
    val averageUnits: Double,
    val overallAverageUnits: Double,
    val uplift: Double,
    val observationDays: Int,
    val weekdayObservationDays: Int,
    val confidence: Double,
)

data class DailyDemandForecast(
    val horizonDays: Int,
    val expectedUnits: Int,
    val lowerUnits: Int,
    val upperUnits: Int,
    val averageDailyUnits: Double,
    val standardDeviation: Double,
    val observationDays: Int,
    val confidence: Double,
    val method: DemandForecastMethod = DemandForecastMethod.STATISTICAL,
)

enum class DemandForecastMethod {
    STATISTICAL,
    LINEAR_REGRESSION,
}

data class DemandModelEvaluation(
    val validationWindows: Int,
    val meanAbsoluteError: Double,
    val meanAbsolutePercentageError: Double,
    val intervalCoverage: Double,
    val passesGate: Boolean,
)

object TinoDailySalesStatistics {
    private const val MINIMUM_OBSERVATION_DAYS = 7
    private const val MINIMUM_CURRENT_UNITS = 3
    private const val Z_SCORE_THRESHOLD = 2.0

    /**
     * Compares the most recently observed day with the preceding observed days.
     * Missing days are not silently treated as zero, and a small sample never
     * becomes an anomaly just to keep the surface active.
     */
    fun detect(salesByDate: Map<LocalDate, Int>): DailySalesAnomaly? {
        val positiveDays = salesByDate
            .filterValues { it > 0 }
            .toSortedMap()
        val currentDate = positiveDays.keys.lastOrNull() ?: return null
        val currentUnits = positiveDays.getValue(currentDate)
        val baseline = positiveDays
            .filterKeys { it != currentDate }
            .values
            .map { it.toDouble() }
        if (currentUnits < MINIMUM_CURRENT_UNITS || baseline.size < MINIMUM_OBSERVATION_DAYS) return null

        val mean = baseline.average()
        val variance = baseline
            .map { (it - mean) * (it - mean) }
            .average()
        val standardDeviation = sqrt(variance)
        val zScore = if (standardDeviation == 0.0) {
            if (currentUnits > mean * 2.0) 3.0 else 0.0
        } else {
            (currentUnits - mean) / standardDeviation
        }
        if (zScore < Z_SCORE_THRESHOLD) return null

        val confidence = when {
            baseline.size >= 21 && zScore >= 3.0 -> 0.88
            baseline.size >= 14 || zScore >= 3.0 -> 0.82
            else -> 0.74
        }
        return DailySalesAnomaly(
            date = currentDate,
            currentUnits = currentUnits,
            baselineMean = mean,
            baselineStandardDeviation = standardDeviation,
            zScore = zScore,
            observationDays = baseline.size,
            confidence = confidence,
        )
    }
}

/**
 * Estimates a weekday effect only when the observed history has enough
 * positive-sale days. Missing dates remain unknown and are not silently
 * converted into zero-sales observations.
 */
object TinoWeekdaySalesStatistics {
    private const val MINIMUM_OBSERVATION_DAYS = 7
    private const val MINIMUM_WEEKDAY_DAYS = 2
    private const val MINIMUM_UPLIFT = 0.5

    fun detect(
        salesByDate: Map<LocalDate, Int>,
        weekday: DayOfWeek,
    ): WeekdaySalesPattern? {
        val observed = salesByDate.filterValues { it > 0 }
        val weekdayValues = observed
            .filterKeys { it.dayOfWeek == weekday }
            .values
        if (observed.size < MINIMUM_OBSERVATION_DAYS || weekdayValues.size < MINIMUM_WEEKDAY_DAYS) {
            return null
        }
        val overallAverage = observed.values.average()
        val weekdayAverage = weekdayValues.average()
        if (overallAverage <= 0.0) return null
        val uplift = (weekdayAverage - overallAverage) / overallAverage
        if (uplift < MINIMUM_UPLIFT) return null
        val confidence = when {
            observed.size >= 21 && weekdayValues.size >= 4 -> 0.84
            observed.size >= 14 && weekdayValues.size >= 3 -> 0.78
            else -> 0.70
        }
        return WeekdaySalesPattern(
            weekday = weekday,
            averageUnits = weekdayAverage,
            overallAverageUnits = overallAverage,
            uplift = uplift,
            observationDays = observed.size,
            weekdayObservationDays = weekdayValues.size,
            confidence = confidence,
        )
    }
}

/**
 * A transparent demand interval based on the mean and dispersion of observed
 * sale days. It is a statistical estimate, not an ML model, and intentionally
 * refuses to fill missing dates with invented zeroes.
 */
object TinoDemandForecastStatistics {
    private const val MINIMUM_OBSERVATION_DAYS = 7
    private const val Z_VALUE = 1.96

    fun forecast(
        salesByDate: Map<LocalDate, Int>,
        horizonDays: Int,
    ): DailyDemandForecast? {
        if (horizonDays <= 0) return null
        val observations = salesByDate.values.filter { it > 0 }
        if (observations.size < MINIMUM_OBSERVATION_DAYS) return null
        val average = observations.average()
        val variance = observations.sumOf { value ->
            val delta = value - average
            delta * delta
        } / observations.size
        val standardDeviation = sqrt(variance)
        val expected = average * horizonDays
        val margin = Z_VALUE * standardDeviation * sqrt(horizonDays.toDouble())
        val confidence = when {
            observations.size >= 21 -> 0.82
            observations.size >= 14 -> 0.76
            else -> 0.68
        }
        return DailyDemandForecast(
            horizonDays = horizonDays,
            expectedUnits = ceil(expected).toInt().coerceAtLeast(1),
            lowerUnits = ceil((expected - margin).coerceAtLeast(0.0)).toInt(),
            upperUnits = ceil(expected + margin).toInt().coerceAtLeast(1),
            averageDailyUnits = average,
            standardDeviation = standardDeviation,
            observationDays = observations.size,
            confidence = confidence,
            method = DemandForecastMethod.STATISTICAL,
        )
    }
}

/**
 * Fits a tiny on-device demand model to observed sale days. It is deliberately
 * conservative: it needs a larger sample than the statistical fallback,
 * ignores unknown dates instead of converting them to zeroes, and exposes a
 * residual interval so callers can explain the uncertainty.
 */
object TinoDemandRegressionModel {
    private const val MINIMUM_OBSERVATION_DAYS = 14
    private const val Z_VALUE = 1.96

    fun forecast(
        salesByDate: Map<LocalDate, Int>,
        horizonDays: Int,
    ): DailyDemandForecast? {
        if (horizonDays <= 0) return null
        val observations = salesByDate
            .filterValues { it > 0 }
            .toSortedMap()
        if (observations.size < MINIMUM_OBSERVATION_DAYS) return null

        val origin = observations.keys.first().toEpochDay().toDouble()
        val points = observations.map { (date, units) ->
            (date.toEpochDay().toDouble() - origin) to units.toDouble()
        }
        val meanX = points.map { it.first }.average()
        val meanY = points.map { it.second }.average()
        val denominator = points.sumOf { (x, _) -> (x - meanX) * (x - meanX) }
        val slope = if (denominator == 0.0) 0.0 else points.sumOf { (x, y) ->
            (x - meanX) * (y - meanY)
        } / denominator
        val intercept = meanY - slope * meanX
        val lastX = observations.keys.last().toEpochDay().toDouble() - origin
        val predictions = (1..horizonDays).map { offset ->
            (intercept + slope * (lastX + offset)).coerceAtLeast(0.0)
        }
        val residuals = points.map { (x, y) -> y - (intercept + slope * x) }
        val residualVariance = residuals.sumOf { it * it } / points.size
        val residualStandardDeviation = sqrt(residualVariance)
        val expected = predictions.sum()
        val margin = Z_VALUE * residualStandardDeviation * sqrt(horizonDays.toDouble())
        val totalVariation = points.sumOf { (_, y) -> (y - meanY) * (y - meanY) }
        val unexplainedVariation = residuals.sumOf { it * it }
        val rSquared = if (totalVariation == 0.0) 1.0
        else (1.0 - unexplainedVariation / totalVariation).coerceIn(0.0, 1.0)
        val confidence = when {
            points.size >= 28 && rSquared >= 0.5 -> 0.82
            points.size >= 21 && rSquared >= 0.3 -> 0.76
            else -> 0.68
        }
        return DailyDemandForecast(
            horizonDays = horizonDays,
            expectedUnits = ceil(expected).toInt().coerceAtLeast(1),
            lowerUnits = ceil((expected - margin).coerceAtLeast(0.0)).toInt(),
            upperUnits = ceil(expected + margin).toInt().coerceAtLeast(1),
            averageDailyUnits = predictions.average(),
            standardDeviation = residualStandardDeviation,
            observationDays = points.size,
            confidence = confidence,
            method = DemandForecastMethod.LINEAR_REGRESSION,
        )
    }
}

/**
 * Evaluates the regression without looking ahead: each validation window is
 * trained only on observations before its target day. A model that does not
 * pass this gate is not promoted by the intelligence surface.
 */
object TinoDemandModelValidator {
    private const val MINIMUM_TRAINING_DAYS = 14
    private const val MINIMUM_VALIDATION_WINDOWS = 3
    private const val MAX_ACCEPTED_MAPE = 0.50
    private const val MINIMUM_INTERVAL_COVERAGE = 0.50

    fun evaluate(
        salesByDate: Map<LocalDate, Int>,
        horizonDays: Int = 1,
    ): DemandModelEvaluation? {
        if (horizonDays <= 0) return null
        val observations = salesByDate
            .filterValues { it > 0 }
            .toSortedMap()
        if (observations.size < MINIMUM_TRAINING_DAYS + MINIMUM_VALIDATION_WINDOWS) return null

        val entries = observations.entries.toList()
        val errors = mutableListOf<Double>()
        val percentageErrors = mutableListOf<Double>()
        var covered = 0
        for (targetIndex in MINIMUM_TRAINING_DAYS until entries.size) {
            val training = entries
                .subList(0, targetIndex)
                .associate { it.key to it.value }
            val target = entries[targetIndex].value.toDouble()
            val forecast = TinoDemandRegressionModel.forecast(training, horizonDays) ?: continue
            val predicted = forecast.expectedUnits.toDouble()
            errors += kotlin.math.abs(predicted - target)
            percentageErrors += kotlin.math.abs(predicted - target) / target.coerceAtLeast(1.0)
            if (target.toInt() in forecast.lowerUnits..forecast.upperUnits) covered++
        }
        if (errors.size < MINIMUM_VALIDATION_WINDOWS) return null
        val mae = errors.average()
        val mape = percentageErrors.average()
        val coverage = covered.toDouble() / errors.size
        return DemandModelEvaluation(
            validationWindows = errors.size,
            meanAbsoluteError = mae,
            meanAbsolutePercentageError = mape,
            intervalCoverage = coverage,
            passesGate = mape <= MAX_ACCEPTED_MAPE && coverage >= MINIMUM_INTERVAL_COVERAGE,
        )
    }
}
