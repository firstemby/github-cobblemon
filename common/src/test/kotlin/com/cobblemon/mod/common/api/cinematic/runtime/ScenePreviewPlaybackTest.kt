/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.BeatTransition
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.util.asExpressionLike
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ScenePreviewPlaybackTest {
    @Test
    fun `preview samples a non-start beat without firing its end transition`() {
        val scene = Scene(id = "preview", startBeatId = "intro",
            beats = mapOf("intro" to Beat("intro"), "second" to Beat("second", duration = 8f)),
            transitions = listOf(BeatTransition("second", "", "1".asExpressionLike(), endsScene = true)))
        val instance = SceneInstance(UUID.randomUUID(), scene, Vec3.ZERO, emptyMap(), previewMode = true)
        assertTrue(instance.start())
        instance.configurePreview("second", 3f, true)
        repeat(3) { instance.tick() }
        assertEquals("second", instance.stateMachine.currentBeat?.id)
        assertEquals(3f, instance.stateMachine.beatTime)
        assertEquals(3f, instance.stateMachine.totalTime)
        assertTrue(instance.isActive())
        instance.configurePreview("second", 1f, true)
        instance.tick()
        assertEquals(1f, instance.stateMachine.beatTime)
        instance.stop()
        assertFalse(instance.isActive())
    }

    @Test
    fun `normal runtime cannot receive preview controls`() {
        val scene = Scene(id = "normal", startBeatId = "main", beats = mapOf("main" to Beat("main")))
        val instance = SceneInstance(UUID.randomUUID(), scene, Vec3.ZERO, emptyMap())
        instance.start()
        assertThrows(IllegalStateException::class.java) { instance.configurePreview("main", 1f, true) }
        instance.stop()
    }
}
