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
import kotlin.math.floor
import net.minecraft.world.phys.Vec3

interface WaypointMotor {
    val position: Vec3
    val yaw: Float
    val pose: PoseType
    val onGround: Boolean
    val jumpCount: Int
    val ready: Boolean
    val arrived: Boolean
    val error: Component?
    fun navigate(destination: Vec3, speedMultiplier: Float): Boolean
    fun step()
    fun stop()
}

class WaypointPlayback(private val track: WaypointPathfinding, private val motor: WaypointMotor) {
    val position get() = motor.position
    val yaw get() = motor.yaw
    val pose get() = motor.pose
    val onGround get() = motor.onGround
    val jumpCount get() = motor.jumpCount
    var error: Component? = motor.error
        private set
    var finished = false
        private set
    var waypointIndex = 1
        private set
    private var frame = 0
    private var navigating = false
    private var progressPosition = position
    private var stalledFrames = 0
    private var replans = 0

    fun close() = motor.stop()

    fun sample(seconds: Float) {
        val target = floor(seconds.coerceIn(0f, track.duration).toDouble() * 20 + 0.00001).toInt()
        require(target >= frame) { "Recreate native waypoint playback before seeking backwards" }
        while (frame < target && error == null) {
            frame++
            step()
        }
        if (seconds >= track.duration && !finished && error == null) fail(Component.translatable("cobblemon.cinematic_editor.route.time_budget"))
    }

    private fun step() {
        if (!finished && !navigating && motor.ready) {
            if (!motor.navigate(track.controlPoints[waypointIndex], track.speedMultiplier)) {
                fail(Component.translatable("cobblemon.cinematic_editor.route.waypoint_error", waypointIndex + 1, motor.error ?: Component.translatable("cobblemon.cinematic_editor.route.no_route")))
                return
            }
            navigating = true
        }
        motor.step()
        motor.error?.let { fail(it); return }
        if (finished || !navigating) return
        if (motor.arrived) {
            motor.stop()
            waypointIndex++
            navigating = false
            stalledFrames = 0
            replans = 0
            progressPosition = position
            finished = waypointIndex >= track.controlPoints.size
            return
        }
        if (position.distanceToSqr(progressPosition) >= 0.0001) {
            progressPosition = position
            stalledFrames = 0
        } else {
            stalledFrames++
            if (stalledFrames >= 40) {
                if (++replans > 3) { fail(Component.translatable("cobblemon.cinematic_editor.route.stuck", waypointIndex + 1)); return }
                motor.stop()
                navigating = false
                stalledFrames = 0
            }
        }
    }

    private fun fail(message: Component) {
        error = message
        motor.stop()
    }
}
