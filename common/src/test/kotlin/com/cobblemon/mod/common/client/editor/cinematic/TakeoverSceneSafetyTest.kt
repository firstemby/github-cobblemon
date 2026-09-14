package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeDamageImmunity
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TakeoverSceneSafetyTest {
    @Test
    fun `safe mode respects an explicit disable until another scene is opened`() {
        val state = EditorPlayState()
        val first = EditorScene()
        val second = EditorScene()
        try {
            TakeoverWatchdog.onEnterTakeover(state, first)
            assertTrue(first.damageImmunityDuringTakeover)
            first.execute(ChangeDamageImmunity(true, false))
            TakeoverWatchdog.onEnterTakeover(state, first)
            assertFalse(first.damageImmunityDuringTakeover)

            state.resetTakeoverSafety()
            TakeoverWatchdog.onEnterTakeover(state, second)
            assertTrue(second.damageImmunityDuringTakeover)
            assertFalse(first.damageImmunityDuringTakeover)
        } finally {
            TakeoverWatchdog.clear()
        }
    }
}
