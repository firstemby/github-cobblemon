package com.cobblemon.mod.common.battles.stadium

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StadiumDecisionClockTest {
    @Test fun `clocks pause without decisions and do not reset on retransmit`() {
        val clock = StadiumDecisionClock(30, 10)
        clock.request("p1", 1)
        repeat(100) { clock.tick(emptySet()) }
        assertEquals(30, clock.seconds("p1"))
        assertEquals(600, clock.matchSeconds())
        repeat(20) { clock.tick(setOf("p1")) }
        clock.request("p1", 1)
        assertEquals(29, clock.seconds("p1"))
        assertEquals(599, clock.matchSeconds())
        clock.request("p1", 2)
        assertEquals(30, clock.seconds("p1"))
        assertEquals(599, clock.matchSeconds())
    }

    @Test fun `only undecided sides expire and simultaneous expiry is preserved`() {
        val clock = StadiumDecisionClock(1, -1)
        clock.request("p1", 1)
        clock.request("p2", 1)
        repeat(19) { assertTrue(clock.tick(setOf("p1", "p2")).isEmpty()) }
        assertEquals(setOf("p1", "p2"), clock.tick(setOf("p1", "p2")))
        assertEquals(-1, clock.matchSeconds())
        val separate = StadiumDecisionClock(1, -1)
        separate.request("p1", 1)
        separate.request("p2", 1)
        repeat(19) { separate.tick(setOf("p1")) }
        assertEquals(setOf("p1"), separate.tick(setOf("p1")))
        assertEquals(1, separate.seconds("p2"))
    }

    @Test fun `disabled turn clock still permits a match limit`() {
        val clock = StadiumDecisionClock(-1, 1)
        clock.request("p1", 1)
        repeat(1200) { assertTrue(clock.tick(setOf("p1")).isEmpty()) }
        assertEquals(0, clock.matchTicks)
        assertEquals(-1, clock.seconds("p1"))
    }

    @Test fun `preview requires the full party and healthy leads`() {
        val healthy = listOf(false, true, true)
        assertTrue(StadiumBattleSession.validOrder(listOf(2, 3, 1), healthy, 2))
        assertFalse(StadiumBattleSession.validOrder(listOf(1, 2, 3), healthy, 2))
        assertFalse(StadiumBattleSession.validOrder(listOf(2, 3), healthy, 2))
        assertFalse(StadiumBattleSession.validOrder(listOf(2, 2, 1), healthy, 2))
        assertFalse(StadiumBattleSession.validOrder(listOf(2, 3, 4), healthy, 2))
    }
}
