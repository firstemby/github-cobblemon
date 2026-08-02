/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.util

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Helpful Functions when working with signed distance fields/functions.
 * https://iquilezles.org/articles/distfunctions/
 *
 * @author Jackson
 * @since July 27th, 2026
 */

/** Which distance formula a primitive uses. */
enum class PrimitiveShape {
    BOX,
    ROUNDED_BOX,
    ELLIPSOID
}

/** Tunable constants for the field. */
data class DistanceFieldTuning(
    val smoothingRange: Float = 0.5f,
    val roundingRadius: Float = 0.08f
)

/** A single shape in the field - a center position, half-extents, and which formula to use. */
data class FieldPrimitive(
    val shape: PrimitiveShape,
    val centerX: Float, val centerY: Float, val centerZ: Float,
    val halfWidth: Float, val halfHeight: Float, val halfDepth: Float
)

/** Fast approximate 1/sqrt(x) - the Quake III trick, cheaper than a real sqrt + divide. */
fun fastInverseSqrt(value: Float): Float {
    var x = value
    val halfX = 0.5f * x
    var bits = java.lang.Float.floatToIntBits(x)
    bits = 0x5f3759df - (bits shr 1)
    x = java.lang.Float.intBitsToFloat(bits)
    x *= (1.5f - halfX * x * x)
    return x
}

/** Exact distance from a point to an axis-aligned box. Inputs are relative to the box's own center. */
fun boxDistance(relX: Float, relY: Float, relZ: Float, halfWidth: Float, halfHeight: Float, halfDepth: Float): Float {
    val qx = abs(relX) - halfWidth
    val qy = abs(relY) - halfHeight
    val qz = abs(relZ) - halfDepth
    val ox = max(qx, 0f)
    val oy = max(qy, 0f)
    val oz = max(qz, 0f)
    val outside = sqrt(ox * ox + oy * oy + oz * oz)
    val inside = min(max(qx, max(qy, qz)), 0f)
    return outside + inside
}

/** Same as boxDistance but with rounded corners/edges https://iquilezles.org/articles/distfunctions/ */
fun roundedBoxDistance(relX: Float, relY: Float, relZ: Float, halfWidth: Float, halfHeight: Float, halfDepth: Float, roundingRadius: Float): Float =
    boxDistance(relX, relY, relZ, halfWidth - roundingRadius, halfHeight - roundingRadius, halfDepth - roundingRadius) - roundingRadius

/** Approximate (lower-bound) distance to an ellipsoid https://iquilezles.org/articles/ellipsoids/. */
fun ellipsoidDistance(relX: Float, relY: Float, relZ: Float, halfWidth: Float, halfHeight: Float, halfDepth: Float): Float {
    val px = relX / halfWidth
    val py = relY / halfHeight
    val pz = relZ / halfDepth
    val inverseMagnitude = fastInverseSqrt(px * px + py * py + pz * pz)
    val trueDistance = sqrt(relX * relX + relY * relY + relZ * relZ)
    return trueDistance * (1.0f - inverseMagnitude)
}

/** Distance from a point to one primitive, dispatched by its shape. */
fun distanceToPrimitive(primitive: FieldPrimitive, sampleX: Float, sampleY: Float, sampleZ: Float, tuning: DistanceFieldTuning): Float {
    val relX = sampleX - primitive.centerX
    val relY = sampleY - primitive.centerY
    val relZ = sampleZ - primitive.centerZ
    return when (primitive.shape) {
        PrimitiveShape.BOX -> boxDistance(relX, relY, relZ, primitive.halfWidth, primitive.halfHeight, primitive.halfDepth)
        PrimitiveShape.ROUNDED_BOX -> roundedBoxDistance(relX, relY, relZ, primitive.halfWidth, primitive.halfHeight, primitive.halfDepth, tuning.roundingRadius)
        PrimitiveShape.ELLIPSOID -> ellipsoidDistance(relX, relY, relZ, primitive.halfWidth, primitive.halfHeight, primitive.halfDepth)
    }
}

///** Normalized quadratic smooth-min https://iquilezles.org/articles/smin/ */
//fun smoothMinimum(a: Float, b: Float, smoothingRange: Float): Float {
//    if (smoothingRange <= 0.001f) return min(a, b)
//    val k = smoothingRange * 4.0f
//    val h = (0.5f + 0.5f * (b - a) / k).coerceIn(0f, 1f)
//    return b + (a - b) * h - k * h * (1f - h)
//}
//
///** Folds every primitive's distance together into one field value at a point. */
//fun sampleField(primitives: List<FieldPrimitive>, sampleX: Float, sampleY: Float, sampleZ: Float, tuning: DistanceFieldTuning): Float {
//    var result = Float.POSITIVE_INFINITY
//    for (primitive in primitives) {
//        val distance = distanceToPrimitive(primitive, sampleX, sampleY, sampleZ, tuning)
//        result = if (result == Float.POSITIVE_INFINITY) distance else smoothMinimum(result, distance, tuning.smoothingRange)
//    }
//    return result
//}

/** Normalized quadratic smooth-min https://iquilezles.org/articles/smin/ */
fun smoothMinimum(a: Float, b: Float, smoothingRange: Float): Float {
    if (smoothingRange <= 0.001f) return min(a, b)
    val k = smoothingRange * 4.0f
    val h = (0.5f + 0.5f * (b - a) / k).coerceIn(0f, 1f)
    return b + (a - b) * h - k * h * (1f - h)
}

/** Folds nearby primitive distances together into one field value at a point. */
fun sampleField(primitives: List<FieldPrimitive>, sampleX: Float, sampleY: Float, sampleZ: Float, tuning: DistanceFieldTuning): Float {
    if (primitives.isEmpty()) return Float.POSITIVE_INFINITY

    val distances = FloatArray(primitives.size) { distanceToPrimitive(primitives[it], sampleX, sampleY, sampleZ, tuning) }
    val minDistance = distances.min()

    if (tuning.smoothingRange <= 0.001f) return minDistance

    // Anything more than a few blend-widths past the closest primitive is cut - its
    // contribution would be negligible in a correct blend anyway, but left in, it still
    // drags the chained result down the more of them there are.
    val cutoff = tuning.smoothingRange * 3f

    var result = Float.POSITIVE_INFINITY
    for (distance in distances) {
        if (distance - minDistance > cutoff) continue
        result = if (result == Float.POSITIVE_INFINITY) distance else smoothMinimum(result, distance, tuning.smoothingRange)
    }
    return result
}

/** Finite-difference gradient of the field at a point - points away from the surface, magnitude ~1 for a clean field. */
fun estimateGradient(primitives: List<FieldPrimitive>, point: Vec3, tuning: DistanceFieldTuning, epsilon: Float = 0.05f): Vec3 {
    val px = point.x.toFloat()
    val py = point.y.toFloat()
    val pz = point.z.toFloat()

    val dx = sampleField(primitives, px + epsilon, py, pz, tuning) - sampleField(primitives, px - epsilon, py, pz, tuning)
    val dy = sampleField(primitives, px, py + epsilon, pz, tuning) - sampleField(primitives, px, py - epsilon, pz, tuning)
    val dz = sampleField(primitives, px, py, pz + epsilon, tuning) - sampleField(primitives, px, py, pz - epsilon, tuning)

    val gradient = Vec3(dx.toDouble(), dy.toDouble(), dz.toDouble())
    return if (gradient.lengthSqr() > 1e-8) gradient.normalize() else Vec3(0.0, 1.0, 0.0)
}
