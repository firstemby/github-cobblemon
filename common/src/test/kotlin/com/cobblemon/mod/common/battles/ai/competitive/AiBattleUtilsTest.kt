package com.cobblemon.mod.common.battles.ai.competitive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiBattleUtilsTest {
    @Test
    fun normalizesWeatherIdsToShowdownNames() {
        assertEquals("sunnyday", AiBattleUtils.normalizeWeatherId("sun"))
        assertEquals("sunnyday", AiBattleUtils.normalizeWeatherId("sunnyday"))
        assertEquals("raindance", AiBattleUtils.normalizeWeatherId("rain"))
        assertEquals("raindance", AiBattleUtils.normalizeWeatherId("raindance"))
        assertEquals("sandstorm", AiBattleUtils.normalizeWeatherId("sand"))
        assertEquals("snow", AiBattleUtils.normalizeWeatherId("snowscape"))
        assertEquals("hail", AiBattleUtils.normalizeWeatherId("hail"))
    }

    @Test
    fun detectsStabCaseInsensitively() {
        val types = listOf("Fire", "Flying")
        assertTrue(AiBattleUtils.hasStab("fire", types))
        assertTrue(AiBattleUtils.hasStab("Flying", types))
        assertFalse(AiBattleUtils.hasStab("water", types))
    }
}
