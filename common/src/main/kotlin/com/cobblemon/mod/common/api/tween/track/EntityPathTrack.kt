/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween.track

import com.cobblemon.mod.common.api.tween.*
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

data class Waypoint(
    val position: Vec3,
    val time: Float,
    val easing: String? = null
)

/**
 * Moves an entity along a spline path defined by [waypoints].
 *
 * Optionally applies look-ahead rotation so the entity faces its direction of travel.
 *
 * Usage:
 * ```kotlin
 * val path = EntityPathTrack(npc, listOf(
 *     Waypoint(pos1, 0f),
 *     Waypoint(pos2, 2f, "ease_in_out_quad"),
 *     Waypoint(pos3, 4f, "ease_out_cubic")
 * ), interpolation = InterpolationMethod.BEZIER, lookAhead = true)
 *
 * TweenManager.server.add(TrackPlayer(path))
 * ```
 */
class EntityPathTrack(
    private val entity: Entity,
    waypoints: List<Waypoint>,
    interpolation: InterpolationMethod = InterpolationMethod.LINEAR,
    defaultEasing: String = "linear",
    private val lookAhead: Boolean = false,
    private val lookAheadDelta: Float = 0.05f
) : Track<Vec3> {

    private val innerTrack: KeyframeTrack<Vec3Interpolatable>

    override val duration: Float
        get() = innerTrack.duration

    init {
        require(waypoints.size >= 2) { "EntityPathTrack requires at least 2 waypoints" }

        val keyframes = waypoints.map { wp ->
            Keyframe(wp.time, Vec3Interpolatable(wp.position))
        }
        val easingMap = waypoints.withIndex()
            .filter { (_, wp) -> wp.easing != null }
            .associate { (idx, wp) -> idx to wp.easing!! }

        innerTrack = KeyframeTrack(
            keyframes = keyframes,
            method = interpolation,
            defaultEasing = defaultEasing,
            getEasing = if (easingMap.isNotEmpty()) { value ->
                val idx = keyframes.indexOfFirst { it.value == value }
                easingMap[idx]
            } else null
        )
    }

    override fun evaluate(time: Float): Vec3? {
        val pos = innerTrack.evaluate(time)?.vec ?: return null

        entity.setPos(pos.x, pos.y, pos.z)

        if (lookAhead) {
            val futureTime = (time + lookAheadDelta).coerceAtMost(duration)
            val futurePos = innerTrack.evaluate(futureTime)?.vec
            if (futurePos != null && futurePos.distanceToSqr(pos) > 0.0001) {
                val direction = futurePos.subtract(pos).normalize()
                val yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z)).toFloat()
                val pitch = Math.toDegrees(Math.atan2(-direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z))).toFloat()
                entity.yRot = yaw
                entity.xRot = pitch
            }
        }

        return pos
    }
}
