package com.shuremind.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PriorityEngineTest {

    // --- Boost table: >30d->0, <=30d->+0.5, <=14d->+1, <=7d->+1.5, <=2d->+2, overdue->+3 ---

    @Test
    fun `boost is zero beyond 30 days`() {
        assertEquals(0.0, PriorityEngine.urgencyBoost(31L), 0.0)
        assertEquals(0.0, PriorityEngine.urgencyBoost(365L), 0.0)
    }

    @Test
    fun `boost is 0_5 at the 30 day boundary`() {
        assertEquals(0.5, PriorityEngine.urgencyBoost(30L), 0.0)
        assertEquals(0.5, PriorityEngine.urgencyBoost(15L), 0.0)
    }

    @Test
    fun `boost is 1 at the 14 day boundary`() {
        assertEquals(1.0, PriorityEngine.urgencyBoost(14L), 0.0)
        assertEquals(1.0, PriorityEngine.urgencyBoost(8L), 0.0)
    }

    @Test
    fun `boost is 1_5 at the 7 day boundary`() {
        assertEquals(1.5, PriorityEngine.urgencyBoost(7L), 0.0)
        assertEquals(1.5, PriorityEngine.urgencyBoost(3L), 0.0)
    }

    @Test
    fun `boost is 2 at the 2 day boundary`() {
        assertEquals(2.0, PriorityEngine.urgencyBoost(2L), 0.0)
        assertEquals(2.0, PriorityEngine.urgencyBoost(0L), 0.0)
    }

    @Test
    fun `boost is 3 when overdue`() {
        assertEquals(3.0, PriorityEngine.urgencyBoost(-1L), 0.0)
        assertEquals(3.0, PriorityEngine.urgencyBoost(-100L), 0.0)
    }

    @Test
    fun `boost is zero when there is no due date (e_g_ SOMEDAY)`() {
        assertEquals(0.0, PriorityEngine.urgencyBoost(null), 0.0)
    }

    // --- Full score formula ---

    @Test
    fun `score formula matches DATA_MODEL for a far-future item`() {
        // impact=1, urgency=1, no boost -> 100*(0.4*1+0.6*1)/3 = 100*1.0/3 = 33.33 -> rounds to 33
        assertEquals(33, PriorityEngine.computeScore(impact = 1, urgency = 1, daysToDue = 60L))
    }

    @Test
    fun `score formula matches DATA_MODEL for an overdue high-impact item`() {
        // impact=3, urgency=3, overdue -> urgency_eff = min(3, 3+3)=3 -> 100*(0.4*3+0.6*3)/3 = 100*3/3 = 100
        assertEquals(100, PriorityEngine.computeScore(impact = 3, urgency = 3, daysToDue = -1L))
    }

    @Test
    fun `urgency_eff clamps at 3 even when urgency plus boost would exceed it`() {
        // urgency=3 + overdue boost(3) would be 6, clamped to 3
        val clamped = PriorityEngine.computeScore(impact = 0, urgency = 3, daysToDue = -5L)
        val atCap = PriorityEngine.computeScore(impact = 0, urgency = 3, daysToDue = 2L) // urgency 3 + boost 2 = 5, also clamps to 3
        assertEquals(clamped, atCap)
    }

    @Test
    fun `dentist acceptance case, impact 3, urgency default, growing as due approaches`() {
        // Dentist: impact=3, urgency=0 here (kept below the clamp so the trend isn't masked by
        // urgency_eff saturating at 3 before "overdue" — see the clamp test above for that case).
        val farScore = PriorityEngine.computeScore(impact = 3, urgency = 0, daysToDue = 60L)
        val nearScore = PriorityEngine.computeScore(impact = 3, urgency = 0, daysToDue = 1L)
        val overdueScore = PriorityEngine.computeScore(impact = 3, urgency = 0, daysToDue = -1L)
        assertEquals(true, farScore < nearScore)
        assertEquals(true, nearScore < overdueScore)
    }

    // --- D-39: priority chip color bands (boundaries 39/40, 59/60, 79/80) ---

    @Test
    fun `39 is neutral and 40 is blue`() {
        assertEquals(PriorityBand.NEUTRAL, PriorityEngine.bandFor(39))
        assertEquals(PriorityBand.BLUE, PriorityEngine.bandFor(40))
    }

    @Test
    fun `59 is blue and 60 is orange`() {
        assertEquals(PriorityBand.BLUE, PriorityEngine.bandFor(59))
        assertEquals(PriorityBand.ORANGE, PriorityEngine.bandFor(60))
    }

    @Test
    fun `79 is orange and 80 is red`() {
        assertEquals(PriorityBand.ORANGE, PriorityEngine.bandFor(79))
        assertEquals(PriorityBand.RED, PriorityEngine.bandFor(80))
    }

    @Test
    fun `0 is neutral and 100 is red`() {
        assertEquals(PriorityBand.NEUTRAL, PriorityEngine.bandFor(0))
        assertEquals(PriorityBand.RED, PriorityEngine.bandFor(100))
    }
}
