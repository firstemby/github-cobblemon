/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai

import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.FloatSpring
import com.cobblemon.mod.common.util.math.simpleSpringDamper
import kotlin.math.sqrt
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.control.BodyRotationControl

/**
 * Controls body movement of pokemon. Runs on both sides.
 *
 * Server: the body always faces yRot. When stationary and the head is at its limit, yRot turns toward the head.
 * Client: the body springs toward the synced yRot so heading changes from the server are smoothed out.
 */
class PokemonBodyRotationControl(private val pokemon: PokemonEntity) : BodyRotationControl(pokemon) {
    companion object {
        // Grounded mons turn in big steps so their feet don't slide, floating ones drift round
        const val GROUNDED_BODY_TURN_FRACTION = 0.8F
        const val FLOATING_BODY_TURN_FRACTION = 0.1F

        // Turn halflife is the time taken to travel this many body widths. Slow mons turn around gently and fast
        // ones don't crab walk
        const val TURN_HALFLIFE_BODY_WIDTHS = 0.5F
        const val MIN_TURN_HALFLIFE_SECONDS = 0.05F
        const val MAX_TURN_HALFLIFE_SECONDS = 0.5F
        const val SECONDS_PER_TICK = 0.05F
    }

    private var bodyYawSpring: FloatSpring? = null

    override fun clientTick() {
        // Riding sets rotations itself
        if (pokemon.controllingPassenger != null) {
            bodyYawSpring = null
            super.clientTick()
            return
        }
        if (mob.level().isClientSide) {
            smoothTowardsHeading()
        } else {
            followHeading()
        }
    }

    private fun followHeading() {
        if (!isMoving()) {
            val bodyTurnRequest = (pokemon.lookControl as? PokemonLookControl)?.bodyTurnRequest ?: 0F
            if (bodyTurnRequest != 0F) {
                val pose = pokemon.getCurrentPoseType()
                val floating = pose in PoseType.FLYING_POSES || pose in PoseType.SWIMMING_POSES
                val fraction = if (floating) FLOATING_BODY_TURN_FRACTION else GROUNDED_BODY_TURN_FRACTION
                pokemon.yRot += Mth.wrapDegrees(pokemon.yHeadRot - pokemon.yRot) * fraction
            }
        }

        pokemon.yBodyRot = pokemon.yRot
        if (isMoving()) {
            rotateHeadIfNecessary()
            lastStableYHeadRot = mob.yHeadRot
            headStableTime = 0
        }
    }

    private fun smoothTowardsHeading() {
        val spring = bodyYawSpring ?: FloatSpring(pokemon.yRot).also { bodyYawSpring = it }

        val dx = pokemon.x - pokemon.xo
        val dz = pokemon.z - pokemon.zo
        val speed = sqrt(dx * dx + dz * dz).toFloat()
        val halflife = if (speed > 0F) {
            (pokemon.bbWidth * TURN_HALFLIFE_BODY_WIDTHS / speed * SECONDS_PER_TICK)
                .coerceIn(MIN_TURN_HALFLIFE_SECONDS, MAX_TURN_HALFLIFE_SECONDS)
        } else {
            MAX_TURN_HALFLIFE_SECONDS
        }

        simpleSpringDamper(spring, pokemon.yRot, halflife, SECONDS_PER_TICK, isAngle = true)
        // Keep continuous with last tick so plain lerps between yBodyRotO and yBodyRot don't spin at the wrap
        pokemon.yBodyRot = pokemon.yBodyRotO + Mth.wrapDegrees(spring.value - pokemon.yBodyRotO)
    }
}
