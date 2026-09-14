package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline
import kotlin.math.abs

data class TimeAxis(
    val originX: Float,
    val widthPx: Float,
    val pixelsPerSecond: Float,
    val scrollSeconds: Float
) {
    fun timeToX(time: Float): Float = originX + (time - scrollSeconds) * pixelsPerSecond
    fun xToTime(x: Float): Float = scrollSeconds + (x - originX) / pixelsPerSecond

    fun visibleRange(): Pair<Float, Float> = scrollSeconds to (scrollSeconds + widthPx / pixelsPerSecond)

    fun zoomedAroundCursor(cursorX: Float, factor: Float): TimeAxis {
        val pivotTime = xToTime(cursorX)
        val newPps = (pixelsPerSecond * factor).coerceIn(2f, 4000f)
        val newScroll = pivotTime - (cursorX - originX) / newPps
        return copy(pixelsPerSecond = newPps, scrollSeconds = newScroll)
    }

    fun fittedTo(duration: Float, marginSeconds: Float = 0.25f): TimeAxis {
        val span = (duration + marginSeconds * 2f).coerceAtLeast(0.1f)
        return copy(
            pixelsPerSecond = (widthPx / span).coerceIn(2f, 4000f),
            scrollSeconds = -marginSeconds
        )
    }

    fun clampedScrollFor(duration: Float, marginSeconds: Float = 0.25f): Float {
        val visible = widthPx / pixelsPerSecond
        val maxScroll = (duration - visible + marginSeconds).coerceAtLeast(-marginSeconds)
        return scrollSeconds.coerceIn(-marginSeconds, maxScroll)
    }

    fun scrollToFollow(time: Float): Float {
        val visible = widthPx / pixelsPerSecond
        return if (time < scrollSeconds || time > scrollSeconds + visible) time - visible * 0.5f
        else scrollSeconds
    }

    fun nearestIndex(mouseX: Float, times: List<Float>, hitRadiusPx: Float): Int {
        var best = -1
        var bestDist = Float.POSITIVE_INFINITY
        times.forEachIndexed { i, t ->
            val d = abs(mouseX - timeToX(t))
            if (d <= hitRadiusPx && d < bestDist) { best = i; bestDist = d }
        }
        return best
    }

    fun niceTickStep(targetPx: Float = 80f): Float {
        val rawSeconds = targetPx / pixelsPerSecond
        val mag = Math.pow(10.0, Math.floor(Math.log10(rawSeconds.toDouble()))).toFloat()
        val norm = rawSeconds / mag
        val step = when { norm < 1.5f -> 1f; norm < 3.5f -> 2f; norm < 7.5f -> 5f; else -> 10f }
        return step * mag
    }
}
