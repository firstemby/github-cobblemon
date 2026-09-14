/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

import org.joml.Vector2f
import kotlin.math.hypot

enum class GizmoAxis { X, Y, Z }

object GizmoHandles {

    const val TOLERANCE_PX = 8f

    /** An arm shorter than this points nearly at the camera; a pixel of drag would swing metres. */
    const val MIN_ARM_PX = 12f

    fun distanceToSegment(p: Vector2f, a: Vector2f, b: Vector2f): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lenSq = abx * abx + aby * aby
        if (lenSq <= 1e-6f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + abx * t), p.y - (a.y + aby * t))
    }

    fun hit(
        mouse: Vector2f,
        origin: Vector2f,
        tips: Map<GizmoAxis, Vector2f>,
        tolerancePx: Float = TOLERANCE_PX
    ): GizmoAxis? {
        var best: GizmoAxis? = null
        var bestDist = Float.POSITIVE_INFINITY
        tips.forEach { (axis, tip) ->
            if (hypot(tip.x - origin.x, tip.y - origin.y) < MIN_ARM_PX) return@forEach
            val d = distanceToSegment(mouse, origin, tip)
            if (d <= tolerancePx && d < bestDist) {
                best = axis
                bestDist = d
            }
        }
        return best
    }

    fun axisDelta(
        dragStart: Vector2f,
        mouse: Vector2f,
        origin: Vector2f,
        tip: Vector2f,
        armWorldLength: Float
    ): Float {
        val ax = tip.x - origin.x
        val ay = tip.y - origin.y
        val lenSq = ax * ax + ay * ay
        if (lenSq <= 1e-6f) return 0f
        val fraction = ((mouse.x - dragStart.x) * ax + (mouse.y - dragStart.y) * ay) / lenSq
        return fraction * armWorldLength
    }
}
