package com.shuremind.engine

import kotlin.math.roundToInt

/** D-39: main-list priority chip color band (bounds live in EngineTuning next to the score weights). */
enum class PriorityBand { NEUTRAL, BLUE, ORANGE, RED }

/** score = round(100 x (0.4*impact + 0.6*urgency_eff) / 3), urgency_eff = min(3, urgency + boost). */
object PriorityEngine {

    fun computeScore(impact: Int, urgency: Int, daysToDue: Long?): Int {
        val urgencyEff = minOf(EngineTuning.MAX_URGENCY_EFF, urgency + urgencyBoost(daysToDue))
        val raw = EngineTuning.PRIORITY_SCALE *
            (EngineTuning.IMPACT_WEIGHT * impact + EngineTuning.URGENCY_WEIGHT * urgencyEff) /
            EngineTuning.SCORE_DIVISOR
        return raw.roundToInt()
    }

    /** daysToDue == null means far-future/no deadline (e.g. SOMEDAY); negative means overdue. */
    fun urgencyBoost(daysToDue: Long?): Double = when {
        daysToDue == null -> EngineTuning.DEFAULT_URGENCY_BOOST
        daysToDue < 0 -> EngineTuning.OVERDUE_URGENCY_BOOST
        else -> EngineTuning.URGENCY_BOOST_CURVE.firstOrNull { daysToDue <= it.maxDaysToDue }?.boost
            ?: EngineTuning.DEFAULT_URGENCY_BOOST
    }

    /** D-39: which color band a 0-100 score falls into, for the main-list priority chip. */
    fun bandFor(score: Int): PriorityBand = when {
        score <= EngineTuning.PRIORITY_BAND_NEUTRAL_MAX -> PriorityBand.NEUTRAL
        score <= EngineTuning.PRIORITY_BAND_BLUE_MAX -> PriorityBand.BLUE
        score <= EngineTuning.PRIORITY_BAND_ORANGE_MAX -> PriorityBand.ORANGE
        else -> PriorityBand.RED
    }
}
