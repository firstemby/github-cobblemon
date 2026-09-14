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
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.ActorTrackTypes
import com.cobblemon.mod.common.entity.PoseType
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WaypointPlaybackTest {
    private class Motor : WaypointMotor {
        override var position = Vec3.ZERO
        override val yaw = 45f
        override var pose = PoseType.STAND
        override val onGround = true
        override val jumpCount = 2
        override var ready = true
        override val arrived get() = destination == position
        override var error: Component? = null
        var destination: Vec3? = null
        var blocked = false
        var steps = 0
        var stops = 0
        var accepted = true
        var receivedSpeed = 0f
        val targets = mutableListOf<Vec3>()
        override fun navigate(destination: Vec3, speedMultiplier: Float): Boolean {
            this.destination = destination
            targets.add(destination)
            receivedSpeed = speedMultiplier
            return accepted
        }
        override fun step() {
            steps++
            if (!blocked) destination?.let {
                val delta = it.subtract(position)
                position = position.add(delta.normalize().scale(minOf(0.1, delta.length())))
                if (position.distanceToSqr(it) < 0.000001) position = it
                pose = PoseType.WALK
            }
        }
        override fun stop() { destination = null; stops++; pose = PoseType.STAND }
    }
    private val end = Vec3(3.0, 0.0, 0.0)
    private fun track(duration: Float = 30f) = WaypointPathfinding(listOf(Vec3.ZERO, end), durationSecs = duration)

    @Test fun `catalog defaults use native speed multiplier`() {
        val model = WaypointPathfinding.fromJson(ActorTrackTypes.entryFor("pathfinding")!!.defaultBody())
        assertEquals(1f, model.speedMultiplier)
    }

    @Test fun `delegates motion and jump state to native motor`() {
        val motor = Motor()
        val playback = WaypointPlayback(track(), motor)
        playback.sample(0.5f)
        assertEquals(10, motor.steps)
        assertEquals(motor.position, playback.position)
        assertEquals(motor.pose, playback.pose)
        assertEquals(2, playback.jumpCount)
        assertEquals(1f, motor.receivedSpeed)
    }

    @Test fun `visits destinations in authored order`() {
        val motor = Motor()
        val middle = Vec3(1.0, 0.0, 0.0)
        val playback = WaypointPlayback(WaypointPathfinding(listOf(Vec3.ZERO, middle, end)), motor)
        playback.sample(10f)
        assertEquals(listOf(middle, end), motor.targets)
        assertTrue(playback.finished)
        assertEquals(end, playback.position)
        assertEquals(PoseType.STAND, playback.pose)
    }

    @Test fun `arrival is determined by navigator rather than a guessed duration`() {
        val motor = Motor().apply { blocked = true }
        val playback = WaypointPlayback(track(), motor)
        playback.sample(1f)
        assertFalse(playback.finished)
        assertEquals(Vec3.ZERO, playback.position)
        motor.blocked = false
        playback.sample(5f)
        assertTrue(playback.finished)
    }

    @Test fun `unsupported start continues physics before requesting navigation`() {
        val motor = Motor().apply { ready = false }
        val playback = WaypointPlayback(track(), motor)
        playback.sample(0.5f)
        assertEquals(10, motor.steps)
        assertTrue(motor.targets.isEmpty())
        motor.ready = true
        playback.sample(1f)
        assertEquals(listOf(end), motor.targets)
    }

    @Test fun `replays identical fixed ticks on seek and incremental playback`() {
        val replayMotor = Motor()
        val replay = WaypointPlayback(track(), replayMotor)
        replay.sample(1f)
        val liveMotor = Motor()
        val live = WaypointPlayback(track(), liveMotor)
        for (frame in 0..20) live.sample(frame / 20f)
        assertEquals(replay.position, live.position)
        assertEquals(replayMotor.steps, liveMotor.steps)
    }

    @Test fun `pause never ticks native motion twice`() {
        val motor = Motor()
        val playback = WaypointPlayback(track(), motor)
        playback.sample(0.5f)
        repeat(10) { playback.sample(0.5f) }
        assertEquals(10, motor.steps)
    }

    @Test fun `stuck navigation retries then reports a bounded failure`() {
        val motor = Motor().apply { blocked = true }
        val playback = WaypointPlayback(track(), motor)
        playback.sample(20f)
        assertEquals(Component.translatable("cobblemon.cinematic_editor.route.stuck", 2), playback.error)
        assertEquals(4, motor.targets.size)
        assertEquals(160, motor.steps)
    }

    @Test fun `native route rejection does not teleport toward destination`() {
        val motor = Motor().apply { accepted = false }
        val playback = WaypointPlayback(track(), motor)
        playback.sample(1f)
        assertNotNull(playback.error)
        assertEquals(Vec3.ZERO, playback.position)
        assertEquals(0, motor.steps)
    }

    @Test fun `native errors surface to preview and stop movement`() {
        val motor = Motor()
        val playback = WaypointPlayback(track(), motor)
        motor.error = Component.translatable("cobblemon.cinematic_editor.route.unloaded")
        playback.sample(1f)
        assertEquals(motor.error, playback.error)
        assertEquals(PoseType.STAND, playback.pose)
    }

    @Test fun `time budget stops unfinished native navigation`() {
        val motor = Motor()
        val playback = WaypointPlayback(track(0.1f), motor)
        playback.sample(0.1f)
        assertEquals(Component.translatable("cobblemon.cinematic_editor.route.time_budget"), playback.error)
        assertEquals(1, motor.stops)
    }

    @Test fun `release stops the native navigator`() {
        val motor = Motor()
        val playback = WaypointPlayback(track(), motor)
        playback.sample(0.5f)
        playback.close()
        assertNull(motor.destination)
        assertEquals(PoseType.STAND, motor.pose)
    }

    @Test fun `invalid inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { WaypointPathfinding(listOf(Vec3.ZERO)) }
        assertThrows(IllegalArgumentException::class.java) { track().copy(speedMultiplier = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { track().copy(speedMultiplier = 5f) }
        assertThrows(IllegalArgumentException::class.java) { track(-1f) }
        assertThrows(IllegalArgumentException::class.java) { track().copy(controlPoints = listOf(Vec3.ZERO, end.scale(100.0))) }
    }
}
