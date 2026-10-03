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
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.control.BodyRotationControl

/**
 * Controls body movement of pokemon. Runs on both sides.
 *
 * Server: the body always faces yRot. When stationary and the head is at its limit, the body turns toward the head
 * through the heading ease one turn at a time.
 * Client: the body follows the same heading ease as the server, run from the synced heading target.
 */
class PokemonBodyRotationControl(private val pokemon: PokemonEntity) : BodyRotationControl(pokemon) {
    private var headingStarted = false

    companion object {
        // Grounded mons turn in big steps so their feet don't slide, floating ones drift round
        const val GROUNDED_BODY_TURN_FRACTION = 0.8F
        const val FLOATING_BODY_TURN_FRACTION = 0.1F
    }

    override fun clientTick() {
        // Riding sets rotations itself
        if (pokemon.controllingPassenger != null) {
            // Keep the heading with the ride so it doesn't swing round on dismount
            pokemon.heading.settle(pokemon.yRot, snap = true)
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
            // Finish one turn before starting the next so ground mons step round instead of sliding
            if (bodyTurnRequest != 0F && pokemon.stationaryTurnTarget == null) {
                val pose = pokemon.getCurrentPoseType()
                val floating = pose in PoseType.FLYING_POSES || pose in PoseType.SWIMMING_POSES
                val fraction = if (floating) FLOATING_BODY_TURN_FRACTION else GROUNDED_BODY_TURN_FRACTION
                pokemon.stationaryTurnTarget = Mth.wrapDegrees(pokemon.yRot + Mth.wrapDegrees(pokemon.yHeadRot - pokemon.yRot) * fraction)
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
        // Start facing where the pokemon already is instead of turning in from zero
        if (!headingStarted) {
            pokemon.heading.settle(pokemon.yRot, snap = true)
            headingStarted = true
        }
        val target = pokemon.entityData.get(PokemonEntity.HEADING_TARGET)
        if (target.z > 0F) {
            pokemon.heading.steer(target.x, target.y)
        } else {
            // Ease rather than snap so stationary turns from the server don't jump
            pokemon.heading.settle(pokemon.yRot, snap = false)
        }
        // Keep continuous with last tick so plain lerps between yBodyRotO and yBodyRot don't spin at the wrap
        pokemon.yBodyRot = pokemon.yBodyRotO + Mth.wrapDegrees(pokemon.heading.yaw.value - pokemon.yBodyRotO)
    }
}
