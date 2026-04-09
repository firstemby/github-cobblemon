/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai.steering.rules

import com.cobblemon.mod.common.entity.ai.steering.SteerRule
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.entity.pokemon.extensions.steerToTarget
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.cos

// Forward AABB projection obstacle avoidance. Stateful — one instance per entity.
class ObstacleAvoidRule(
    override val entity: PokemonEntity,
    override val priority: Int = 100,
    override val weight: Float = 1.0f,
    override val debugColor: Triple<Float, Float, Float> = Triple(1f, 0f, 0f),
    val maxForce: Double = 0.02,
    val lookAheadTicks: Int = 10,
    val smoothingAlpha: Double = 0.25
) : SteerRule {

    private var prevForce: Vec3 = Vec3.ZERO

    override fun calculate(): Vec3? {
        val topSpeed = (entity.moveControl as? PokemonMoveControl)?.steerSpeed ?: 0.1

        val forwardVec = if (entity.deltaMovement.horizontalDistanceSqr() < 0.001) {
            val yaw = Math.toRadians(entity.yRot.toDouble())
            Vec3(-sin(yaw), 0.0, cos(yaw))
        } else {
            Vec3(entity.deltaMovement.x, 0.0, entity.deltaMovement.z).normalize()
        }

        val lookAheadDist = max(entity.deltaMovement.length() * lookAheadTicks, entity.bbWidth / 2.0)
        val collBox = entity.boundingBox.move(forwardVec.scale(lookAheadDist))
        val shapes = entity.level().getBlockCollisions(entity, collBox)

        val nearestShape = shapes.minByOrNull { shape ->
            shape.bounds().center.distanceToSqr(entity.position())
        }

        if (nearestShape == null) {
            prevForce = prevForce.lerp(Vec3.ZERO, smoothingAlpha)
            return if (prevForce.lengthSqr() < 1e-6) null else prevForce
        }

        val obsCenter = nearestShape.bounds().center
        val toObs = obsCenter.subtract(entity.position())
        val projVec = forwardVec.scale(toObs.dot(forwardVec))
        val lateralComponent = toObs.subtract(projVec)

        val avoidForce = if (lateralComponent.lengthSqr() < 0.001) {
            // Dead ahead — turn right
            val rightVec = forwardVec.cross(Vec3(0.0, 1.0, 0.0))
            entity.steerToTarget(entity.position().add(rightVec), topSpeed, maxForce, horzOnly = true)
        } else {
            var avoidanceVec = Vec3(lateralComponent.x * -1.0, 0.0, lateralComponent.z * -1.0)
            val avoidStrength = sqrt(1.0 - (avoidanceVec.length() / (entity.bbWidth / 2.0 + 0.5)).coerceIn(0.0, 0.7))
            val avoidancePoint = entity.position().add(avoidanceVec)
            entity.steerToTarget(avoidancePoint, topSpeed, maxForce * avoidStrength, horzOnly = true)
        }

        prevForce = prevForce.lerp(avoidForce, smoothingAlpha)
        return prevForce
    }
}
