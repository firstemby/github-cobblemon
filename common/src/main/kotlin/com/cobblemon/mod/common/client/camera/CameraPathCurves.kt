/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * Provides curve interpolation algorithms for smooth camera paths.
 * Includes Catmull-Rom, Cubic Bezier, and Hermite splines.
 *
 * @author Terrainwax
 * @since October 17th, 2025
 */
object CameraPathCurves {

    /**
     * Catmull-Rom spline interpolation for smooth curves through all points.
     * Automatically calculates tangents based on neighboring points.
     * 
     * @param points The control points (keyframe positions)
     * @param t Progress through the entire path (0-1)
     * @return Interpolated position
     */
    fun catmullRom(points: List<Vec3>, t: Float): Vec3 {
        if (points.size < 2) return points.firstOrNull() ?: Vec3.ZERO
        if (points.size == 2) return linearInterp(points[0], points[1], t)
        
        // Find which segment we're in
        val segmentCount = points.size - 1
        val scaledT = t * segmentCount
        val segmentIndex = scaledT.toInt().coerceIn(0, segmentCount - 1)
        val localT = scaledT - segmentIndex
        
        // Get the 4 control points for Catmull-Rom (p0, p1, p2, p3)
        val p0 = points.getOrElse(segmentIndex - 1) { points[segmentIndex] }
        val p1 = points[segmentIndex]
        val p2 = points[segmentIndex + 1]
        val p3 = points.getOrElse(segmentIndex + 2) { points[segmentIndex + 1] }
        
        return catmullRomSegment(p0, p1, p2, p3, localT)
    }

    /**
     * Catmull-Rom segment between p1 and p2, using p0 and p3 for tangent calculation.
     */
    private fun catmullRomSegment(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Float): Vec3 {
        val t2 = t * t
        val t3 = t2 * t
        
        // Catmull-Rom matrix coefficients
        val x = 0.5f * (
            (2f * p1.x) +
            (-p0.x + p2.x) * t +
            (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
            (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
        )
        
        val y = 0.5f * (
            (2f * p1.y) +
            (-p0.y + p2.y) * t +
            (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
            (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
        )
        
        val z = 0.5f * (
            (2f * p1.z) +
            (-p0.z + p2.z) * t +
            (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 +
            (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3
        )
        
        return Vec3(x.toDouble(), y.toDouble(), z.toDouble())
    }

    /**
     * Cubic Bezier curve interpolation.
     * Requires control points to be specified in keyframes.
     * 
     * @param p0 Start point
     * @param p1 First control point
     * @param p2 Second control point
     * @param p3 End point
     * @param t Progress (0-1)
     */
    fun cubicBezier(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Float): Vec3 {
        val oneMinusT = 1f - t
        val oneMinusT2 = oneMinusT * oneMinusT
        val oneMinusT3 = oneMinusT2 * oneMinusT
        val t2 = t * t
        val t3 = t2 * t
        
        val x = oneMinusT3 * p0.x + 
                3f * oneMinusT2 * t * p1.x + 
                3f * oneMinusT * t2 * p2.x + 
                t3 * p3.x
        
        val y = oneMinusT3 * p0.y + 
                3f * oneMinusT2 * t * p1.y + 
                3f * oneMinusT * t2 * p2.y + 
                t3 * p3.y
        
        val z = oneMinusT3 * p0.z + 
                3f * oneMinusT2 * t * p1.z + 
                3f * oneMinusT * t2 * p2.z + 
                t3 * p3.z
        
        return Vec3(x.toDouble(), y.toDouble(), z.toDouble())
    }

    /**
     * Hermite spline interpolation with explicit tangent control.
     * 
     * @param p0 Start point
     * @param m0 Start tangent (velocity)
     * @param p1 End point
     * @param m1 End tangent (velocity)
     * @param t Progress (0-1)
     */
    fun hermite(p0: Vec3, m0: Vec3, p1: Vec3, m1: Vec3, t: Float): Vec3 {
        val t2 = t * t
        val t3 = t2 * t
        
        // Hermite basis functions
        val h00 = 2f * t3 - 3f * t2 + 1f
        val h10 = t3 - 2f * t2 + t
        val h01 = -2f * t3 + 3f * t2
        val h11 = t3 - t2
        
        val x = h00 * p0.x + h10 * m0.x + h01 * p1.x + h11 * m1.x
        val y = h00 * p0.y + h10 * m0.y + h01 * p1.y + h11 * m1.y
        val z = h00 * p0.z + h10 * m0.z + h01 * p1.z + h11 * m1.z
        
        return Vec3(x.toDouble(), y.toDouble(), z.toDouble())
    }

    /**
     * Simple linear interpolation.
     */
    fun linearInterp(from: Vec3, to: Vec3, t: Float): Vec3 {
        return Vec3(
            Mth.lerp(t.toDouble(), from.x, to.x),
            Mth.lerp(t.toDouble(), from.y, to.y),
            Mth.lerp(t.toDouble(), from.z, to.z)
        )
    }

    /**
     * Calculates tangent for Hermite/Catmull-Rom based on neighboring points.
     */
    fun calculateTangent(prev: Vec3, next: Vec3, tension: Float = 0.5f): Vec3 {
        return next.subtract(prev).scale(tension.toDouble())
    }
}

