/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.util

import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/** One shell to draw - a target field value, a color, a particle size. */
data class DistanceFieldLayer(
    val isoValue: Float,
    val color: Vector3f,
    val particleSize: Float = 1.0f
)

/**
 * Marches a grid of vertical columns through the field around [center] and spawns a
 * particle everywhere it crosses one of [layers]' target values. Server-side only.
 */
fun visualizeDistanceField(
    referenceEntity: Entity,
    center: Vec3,
    layers: List<DistanceFieldLayer>,
    shape: PrimitiveShape = PrimitiveShape.BOX,
    horizontalRadius: Double = 4.0,
    verticalRange: Double = 4.0,
    sampleSpacing: Double = 0.5,
    marchStep: Double = 0.1,
    tuning: DistanceFieldTuning = DistanceFieldTuning()
) {
    val level = referenceEntity.level()
    if (level !is ServerLevel) return

    val searchBox = AABB(
        center.x - horizontalRadius, center.y - verticalRange, center.z - horizontalRadius,
        center.x + horizontalRadius, center.y, center.z + horizontalRadius
    )

    val primitives = collectPrimitivesFromBlocks(level, searchBox, shape, includeFluids = true)
    if (primitives.isEmpty()) return

    var x = -horizontalRadius
    while (x <= horizontalRadius) {
        var z = -horizontalRadius
        while (z <= horizontalRadius) {
            marchColumn(level, primitives, center, x, z, verticalRange, marchStep, layers, tuning)
            z += sampleSpacing
        }
        x += sampleSpacing
    }
}

private fun marchColumn(
    level: ServerLevel,
    primitives: List<FieldPrimitive>,
    center: Vec3,
    offsetX: Double,
    offsetZ: Double,
    verticalRange: Double,
    marchStep: Double,
    layers: List<DistanceFieldLayer>,
    tuning: DistanceFieldTuning
) {
    val previousValue = FloatArray(layers.size) { Float.NaN }

    var y = verticalRange
    while (y >= -verticalRange) {
        val worldX = (center.x + offsetX).toFloat()
        val worldY = (center.y + y).toFloat()
        val worldZ = (center.z + offsetZ).toFloat()
        val fieldValue = sampleField(primitives, worldX, worldY, worldZ, tuning)

        for (layerIndex in layers.indices) {
            val layer = layers[layerIndex]
            val previous = previousValue[layerIndex]

            if (!previous.isNaN() && crossedTarget(previous, fieldValue, layer.isoValue)) {
                val t = (layer.isoValue - previous) / (fieldValue - previous)
                val crossingY = (y + marchStep) - marchStep * t
                spawnLayerParticle(level, center.add(offsetX, crossingY, offsetZ), layer)
            }

            previousValue[layerIndex] = fieldValue
        }

        y -= marchStep
    }
}

private fun crossedTarget(previous: Float, current: Float, target: Float): Boolean =
    (previous - target) * (current - target) < 0f

private fun spawnLayerParticle(level: ServerLevel, pos: Vec3, layer: DistanceFieldLayer) {
    val particle = DustParticleOptions(layer.color, layer.particleSize)
    level.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0)
}
