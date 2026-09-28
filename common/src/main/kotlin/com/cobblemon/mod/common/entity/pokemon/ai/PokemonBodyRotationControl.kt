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

        // The server already limits how fast heading changes, so this only smooths the steps between ticks
        const val BODY_TURN_HALFLIFE_SECONDS = 0.175F
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

        simpleSpringDamper(spring, pokemon.yRot, BODY_TURN_HALFLIFE_SECONDS, SECONDS_PER_TICK, isAngle = true)
        // Keep continuous with last tick so plain lerps between yBodyRotO and yBodyRot don't spin at the wrap
        pokemon.yBodyRot = pokemon.yBodyRotO + Mth.wrapDegrees(spring.value - pokemon.yBodyRotO)
    }
}
