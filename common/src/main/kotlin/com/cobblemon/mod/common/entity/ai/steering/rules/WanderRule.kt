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
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// Reynolds wander — projects a sphere ahead and offsets a point on it each tick.
// Stateful: holds current wander angles, one instance per entity.
// https://slsdo.github.io/steering-behaviors/
class WanderRule(
    override val entity: PokemonEntity,
    override val priority: Int = 10,
    override val weight: Float = 1.0f,
    override val debugColor: Triple<Float, Float, Float> = Triple(1f, 0.5f, 0f),
    val maxForce: Double = 0.02,
    val wanderDisp: Double = 15.0,
    val centerDistance: Double = 4.0,
    val wanderRadius: Double = 4.0,
    val horzOnly: Boolean = false //TODO: all of these should be checks if flying or walking
) : SteerRule {

    var currHorzWander = 0.0
    var currVertWander = 0.0

    override fun calculate(): Vec3? {
        val speed = (entity.moveControl as? PokemonMoveControl)?.steerSpeed ?: 0.1

        val normVec = if (entity.deltaMovement.lengthSqr() > 0.0001) {
            entity.lookAngle.normalize()
        } else {
            Vec3(
                Random.nextDouble(-1.0, 1.0),
                Random.nextDouble(-1.0, 1.0),
                Random.nextDouble(-1.0, 1.0)
            ).normalize()
        }

        var wanderOff = normVec

        // Clamp vertical wander when getting too steep
        val currPitchS = sin(Mth.atan2(wanderOff.y, wanderOff.horizontalDistance()))
        if (currPitchS >= 0.5f || currPitchS <= -0.5f) currVertWander = 0.0

        wanderOff = wanderOff.xRot(Math.toRadians(currVertWander).toFloat())
        wanderOff = wanderOff.yRot(Math.toRadians(currHorzWander).toFloat())

        val wanderCenter = entity.position().add(normVec.scale(centerDistance))
        val wanderPoint = wanderCenter.add(wanderOff.scale(wanderRadius))

        // Advance wander angles
        currVertWander = Mth.wrapDegrees(currVertWander + Random.nextDouble(-wanderDisp, wanderDisp))
        currHorzWander = Mth.wrapDegrees(currHorzWander + Random.nextDouble(-wanderDisp, wanderDisp))

        val force = entity.steerToTarget(wanderPoint, speed, maxForce)
        return if (horzOnly) Vec3(force.x, 0.0, force.z) else force
    }
}
