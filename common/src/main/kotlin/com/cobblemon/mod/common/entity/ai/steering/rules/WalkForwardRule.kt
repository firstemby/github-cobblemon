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
import kotlin.math.sin

// Steers toward a point directly ahead of the entity's current velocity/yaw.
// Useful as a baseline test rule.
class WalkForwardRule(
    override val entity: PokemonEntity,
    override val priority: Int = 10,
    override val weight: Float = 1.0f,
    override val debugColor: Triple<Float, Float, Float> = Triple(0f, 1f, 0f),
    val maxForce: Double = 0.02
) : SteerRule {

    override fun calculate(): Vec3? {
        val speed = (entity.moveControl as? PokemonMoveControl)?.steerSpeed ?: 0.1

        val forwardVec = if (entity.deltaMovement.horizontalDistanceSqr() < 0.001) {
            val yaw = Math.toRadians(entity.yRot.toDouble())
            Vec3(-sin(yaw), 0.0, cos(yaw))
        } else {
            Vec3(entity.deltaMovement.x, 0.0, entity.deltaMovement.z).normalize()
        }

        val pointAhead = entity.position().add(forwardVec)
        return entity.steerToTarget(pointAhead, speed, maxForce, horzOnly = true)
    }
}

private fun cos(d: Double) = kotlin.math.cos(d)
private fun sin(d: Double) = kotlin.math.sin(d)
