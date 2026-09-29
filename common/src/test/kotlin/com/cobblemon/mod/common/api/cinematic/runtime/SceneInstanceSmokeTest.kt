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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SceneInstanceSmokeTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        CinematicManager.clear()
    }

    private fun minimalScene(): Scene = Scene(
        id = "test_scene",
        startBeatId = "main",
        beats = mapOf("main" to Beat(id = "main"))
    )

    @Test
    fun `CinematicManager starts and tracks a scene`() {
        val id = CinematicManager.startScene(minimalScene(), origin = Vec3.ZERO)
        assertNotNull(id)
        assertEquals(1, CinematicManager.activeCount())
        assertNotNull(CinematicManager.getScene(id!!))
    }

    @Test
    fun `SceneInstance start succeeds when start beat exists`() {
        val instance = SceneInstance(
            id = UUID.randomUUID(),
            scene = minimalScene(),
            origin = Vec3.ZERO,
            actorBindings = emptyMap()
        )
        assertTrue(instance.start())
        assertTrue(instance.isActive())
    }

    @Test
    fun `tick runs without throwing on a running scene`() {
        val id = CinematicManager.startScene(minimalScene(), origin = Vec3.ZERO)!!
        repeat(10) { CinematicManager.tick() }
        assertEquals(1, CinematicManager.activeCount())
    }

    @Test
    fun `an endsScene transition ends the scene and drops it from the manager`() {
        val scene = Scene(
            id = "ending_scene",
            startBeatId = "main",
            beats = mapOf("main" to Beat(id = "main")),
            transitions = listOf(
                BeatTransition(
                    sourceBeatId = "main",
                    targetBeatId = "",
                    condition = "1.0".asExpressionLike(),
                    endsScene = true
                )
            )
        )
        val id = CinematicManager.startScene(scene, origin = Vec3.ZERO)!!
        val instance = CinematicManager.getScene(id)!!

        CinematicManager.tick()

        assertFalse(instance.isActive())
        assertEquals(0, CinematicManager.activeCount())
    }

    @Test
    fun `stopScene removes the instance`() {
        val id = CinematicManager.startScene(minimalScene(), origin = Vec3.ZERO)!!
        CinematicManager.stopScene(id)
        assertEquals(0, CinematicManager.activeCount())
        assertNull(CinematicManager.getScene(id))
    }

    @Test
    fun `stopping an old scene does not clear a replacement battle mapping`() {
        val battleId = UUID.randomUUID()
        val first = CinematicManager.startScene(minimalScene(), origin = Vec3.ZERO)!!
        val replacement = CinematicManager.startScene(minimalScene(), origin = Vec3.ZERO)!!
        CinematicManager.registerSceneForBattle(battleId, first)
        CinematicManager.registerSceneForBattle(battleId, replacement)

        CinematicManager.stopScene(first)

        assertEquals(replacement, CinematicManager.sceneForBattle(battleId))
        CinematicManager.stopScene(replacement)
        assertNull(CinematicManager.sceneForBattle(battleId))
    }

    @Test
    fun `clear preserves a scene started by a stop listener`() {
        val first = CinematicManager.startScene(minimalScene(), origin = Vec3.ZERO)!!
        var replacement: UUID? = null
        CinematicManager.getScene(first)!!.onStop {
            replacement = CinematicManager.startScene(minimalScene(), origin = Vec3.ZERO)
        }

        CinematicManager.clear()

        assertEquals(1, CinematicManager.activeCount())
        assertNotNull(CinematicManager.getScene(replacement!!))
    }

    @Test
    fun `scene counters release each acquisition exactly once`() {
        val id = CinematicManager.startScene(minimalScene(), origin = Vec3.ZERO)!!
        val instance = CinematicManager.getScene(id)!!
        val first = CinematicManager.acquireSceneCounter(id, "pending")!!
        val second = CinematicManager.acquireSceneCounter(id, "pending")!!
        assertEquals(2.0, instance.getVariable("pending"))

        first.close()
        first.close()
        assertEquals(1.0, instance.getVariable("pending"))
        second.close()
        assertEquals(0.0, instance.getVariable("pending"))
    }

    @Test
    fun `viewer set updates propagate to network sync`() {
        val instance = SceneInstance(
            id = UUID.randomUUID(),
            scene = minimalScene(),
            origin = Vec3.ZERO,
            actorBindings = emptyMap()
        )
        instance.start()
        val viewer = UUID.randomUUID()
        instance.addViewer(viewer)
        assertEquals(setOf(viewer), instance.networkSync.viewerIdsSnapshot())
        assertTrue(instance.removeViewer(viewer))
        assertTrue(instance.networkSync.viewerIdsSnapshot().isEmpty())
    }

    @Test
    fun `startScene returns null when scene has no valid start beat`() {
        // An undeclared start beat passes construction but must fail when the state machine starts.
        val brokenScene = Scene(
            id = "broken",
            startBeatId = "ghost",
            beats = mapOf("other" to Beat(id = "other"))
        )
        val id = CinematicManager.startScene(brokenScene, origin = Vec3.ZERO)
        assertNull(id)
        assertEquals(0, CinematicManager.activeCount())
    }

    @Test
    fun `shotEvaluator returns null for missing shot id`() {
        val instance = SceneInstance(
            id = UUID.randomUUID(),
            scene = minimalScene(),
            origin = Vec3.ZERO,
            actorBindings = emptyMap()
        )
        assertNull(instance.shotEvaluator.evaluate("does_not_exist", 0f))
    }

    @Test
    fun `cutResolver returns null when no cut track exists for feed`() {
        val resolver = CutResolver()
        val beat = Beat(id = "main")
        assertNull(resolver.resolveLiveShot(beat, feedId = "primary", beatTime = 0f))
    }
}
