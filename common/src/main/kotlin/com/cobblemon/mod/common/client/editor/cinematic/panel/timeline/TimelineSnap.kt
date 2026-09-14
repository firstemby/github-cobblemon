package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline
import kotlin.math.abs

object TimelineSnap {
    fun snap(proposed: Float, candidates: List<Float>, thresholdSeconds: Float): Float {
        var best = proposed; var bestDist = thresholdSeconds
        for (c in candidates) {
            val d = abs(proposed - c)
            if (d <= bestDist) { best = c; bestDist = d }
        }
        return best
    }
}
