/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding
import com.cobblemon.mod.common.entity.PoseType
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

data class WaypointRouteFrame(val position: Vec3, val yaw: Float, val pose: PoseType, val onGround: Boolean)

class WaypointRoutePreview(private val track: WaypointPathfinding, motor: WaypointMotor) {
    val playback = WaypointPlayback(track, motor)
    val frames = mutableListOf(frame())
    private val totalTicks = ceil(track.duration * 20.0).toInt()
    private var ticks = 0
    val done get() = playback.finished || playback.error != null || ticks >= totalTicks
    val seconds get() = minOf(ticks / 20f, track.duration)

    fun step() {
        if (done) return
        ticks++
        playback.sample(seconds)
        frames += frame()
    }

    fun close() = playback.close()
    private fun frame() = WaypointRouteFrame(playback.position, playback.yaw, playback.pose, playback.onGround)
}
