/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding
import com.cobblemon.mod.common.entity.PoseType
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WaypointRoutePreviewTest {
    private class Motor : WaypointMotor {
        override var position = Vec3.ZERO
        override val yaw = 90f
        override var pose = PoseType.WALK
        override val onGround = false
        override val jumpCount = 1
        override val ready = true
        override val arrived get() = position.x >= 1.0
        override var error: Component? = null
        var rejected = false
        var stops = 0
        override fun navigate(destination: Vec3, speedMultiplier: Float) = !rejected
        override fun step() { position = position.add(0.25, 0.1, 0.0) }
        override fun stop() { stops++ }
    }
    private fun track(duration: Float = 30f) = WaypointPathfinding(listOf(Vec3.ZERO, Vec3(1.0, 0.0, 0.0)), durationSecs = duration)

    @Test fun `captures the start and each physics tick until arrival`() {
        val preview = WaypointRoutePreview(track(), Motor())
        repeat(100) { preview.step() }
        assertTrue(preview.done)
        assertTrue(preview.playback.finished)
        assertEquals(5, preview.frames.size)
        assertEquals(Vec3.ZERO, preview.frames.first().position)
        assertEquals(Vec3(1.0, 0.4, 0.0), preview.frames.last().position)
        assertEquals(0.2f, preview.seconds)
        assertFalse(preview.frames.last().onGround)
        assertEquals(90f, preview.frames.last().yaw)
    }

    @Test fun `precompute matches runtime playback exactly`() {
        val live = WaypointPlayback(track(), Motor())
        val preview = WaypointRoutePreview(track(), Motor())
        for (tick in 1..4) {
            live.sample(tick / 20f)
            preview.step()
            assertEquals(live.position, preview.frames.last().position)
            assertEquals(live.pose, preview.frames.last().pose)
        }
    }

    @Test fun `rejected search retains the start and identifies the waypoint`() {
        val preview = WaypointRoutePreview(track(), Motor().apply { rejected = true; error = null })
        preview.step()
        assertTrue(preview.done)
        assertFalse(preview.playback.finished)
        assertEquals(Component.translatable("cobblemon.cinematic_editor.route.waypoint_error", 2,
            Component.translatable("cobblemon.cinematic_editor.route.no_route")), preview.playback.error)
        assertTrue(preview.frames.all { it.position == Vec3.ZERO })
    }

    @Test fun `initial errors do not run physics`() {
        val preview = WaypointRoutePreview(track(), Motor().apply { error = Component.translatable("cobblemon.cinematic_editor.route.first_obstructed") })
        preview.step()
        assertEquals(1, preview.frames.size)
        assertEquals(0f, preview.seconds)
        assertTrue(preview.done)
    }

    @Test fun `track deadline retains partial motion`() {
        val preview = WaypointRoutePreview(track(0.1f), Motor())
        repeat(10) { preview.step() }
        assertTrue(preview.done)
        assertEquals(3, preview.frames.size)
        assertEquals(0.1f, preview.seconds)
        assertEquals(Component.translatable("cobblemon.cinematic_editor.route.time_budget"), preview.playback.error)
    }

    @Test fun `fractional tick deadline terminates with bounded frame count`() {
        val preview = WaypointRoutePreview(track(0.075f), Motor())
        repeat(10) { preview.step() }
        assertTrue(preview.done)
        assertEquals(3, preview.frames.size)
        assertEquals(0.075f, preview.seconds)
    }

    @Test fun `closing cancels the native motor`() {
        val motor = Motor()
        WaypointRoutePreview(track(), motor).close()
        assertEquals(1, motor.stops)
    }
}
