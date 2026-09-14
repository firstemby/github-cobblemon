package com.cobblemon.mod.common.client.render.camera.compositor.layout

import kotlin.math.cos
import kotlin.math.sin

internal object SplitLayoutGeometry {
    data class Point(val x: Double, val y: Double)
    data class Regions(val primary: List<Point>, val secondary: List<Point>, val gap: List<Point>)

    fun straight(width: Float, height: Float, vertical: Boolean, ratio: Float, gap: Float): Regions =
        split(width, height, if (vertical) 0.0 else 1.0, if (vertical) 1.0 else 0.0,
            (if (vertical) height else width).toDouble() * ratio, gap)

    fun diagonal(width: Float, height: Float, angle: Float, gap: Float): Regions {
        val radians = Math.toRadians(Math.IEEEremainder(angle.toDouble(), 180.0))
        val nx = cos(radians)
        val ny = sin(radians)
        return split(width, height, nx, ny, nx * width / 2.0 + ny * height / 2.0, gap)
    }

    private fun split(width: Float, height: Float, nx: Double, ny: Double, offset: Double, gap: Float): Regions {
        val rectangle = listOf(Point(0.0, 0.0), Point(width.toDouble(), 0.0),
            Point(width.toDouble(), height.toDouble()), Point(0.0, height.toDouble()))
        val halfGap = gap.toDouble() / 2.0
        return Regions(
            clip(rectangle, nx, ny, offset - halfGap),
            clip(rectangle, -nx, -ny, -offset - halfGap),
            if (gap == 0f) emptyList() else clip(clip(rectangle, nx, ny, offset + halfGap), -nx, -ny, -offset + halfGap)
        )
    }

    private fun clip(polygon: List<Point>, nx: Double, ny: Double, offset: Double): List<Point> {
        if (polygon.isEmpty()) return emptyList()
        val result = mutableListOf<Point>()
        var previous = polygon.last()
        var previousDistance = nx * previous.x + ny * previous.y - offset
        for (point in polygon) {
            val distance = nx * point.x + ny * point.y - offset
            if ((distance <= 0.0) != (previousDistance <= 0.0)) {
                // Signed distances give the edge intersection without a tangent singularity at 90 degrees.
                val fraction = previousDistance / (previousDistance - distance)
                result += Point(previous.x + (point.x - previous.x) * fraction,
                    previous.y + (point.y - previous.y) * fraction)
            }
            if (distance <= 0.0) result += point
            previous = point
            previousDistance = distance
        }
        return result
    }

    fun textureCoordinates(polygon: List<Point>, width: Float, height: Float): List<Point> {
        if (polygon.isEmpty()) return emptyList()
        val centerX = (polygon.minOf { it.x } + polygon.maxOf { it.x }) / 2.0
        val centerY = (polygon.minOf { it.y } + polygon.maxOf { it.y }) / 2.0
        return polygon.map { Point((0.5 + (it.x - centerX) / width).coerceIn(0.0, 1.0),
            (0.5 - (it.y - centerY) / height).coerceIn(0.0, 1.0)) }
    }
}
