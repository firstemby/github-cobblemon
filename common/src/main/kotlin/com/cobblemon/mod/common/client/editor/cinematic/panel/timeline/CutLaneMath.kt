/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

object CutLaneMath {

    fun clampToMonotonic(
        cutTimes: List<Float>,
        index: Int,
        proposedTime: Float,
        beatDuration: Float,
        minGap: Float = 0.05f
    ): Float {
        val previous = if (index > 0) cutTimes[index - 1] else 0f
        val next = maxOf(previous, cutTimes.getOrNull(index + 1) ?: beatDuration)
        val lowBound = if (index > 0) previous + minGap else previous
        val highBound = if (index < cutTimes.size - 1) next - minGap else next
        // Drop the preferred gap when cuts coincide; never move past a neighbor.
        return if (lowBound <= highBound) proposedTime.coerceIn(lowBound, highBound)
            else proposedTime.coerceIn(previous, next)
    }

    fun blockBounds(cutTimes: List<Float>, index: Int, axis: TimeAxis, beatDuration: Float): Pair<Float, Float> {
        val start = cutTimes[index]
        val end = if (index + 1 < cutTimes.size) cutTimes[index + 1] else beatDuration
        return axis.timeToX(start) to axis.timeToX(end)
    }

    fun blockAt(mouseX: Float, blockBoundsPx: List<Pair<Float, Float>>): Int {
        blockBoundsPx.forEachIndexed { i, (x0, x1) -> if (mouseX in x0..x1) return i }
        return -1
    }
}
