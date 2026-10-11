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
import kotlin.math.abs
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.control.BodyRotationControl

/**
 * Controls body movement of pokemon. Runs on both sides.
 *
 * Server: the body always faces yRot. When standing still it turns towards what it's looking at through the heading
 * ease. Ground mons turn once, just far enough for the head to reach. Floating ones keep following the look.
 * Client: the body follows the same heading ease as the server, run from the synced heading target.
 */
class PokemonBodyRotationControl(private val pokemon: PokemonEntity) : BodyRotationControl(pokemon) {
    private var headingStarted = false

    companion object {
        // Share of the head's yaw limit a standing ground mon is happy to look across before turning its body.
        // Posers usually limit the head well below the full limit so the body turns to keep things in that range
        const val STANDING_TURN_HEAD_REACH_FRACTION = 0.25F
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
            val lookYaw = (pokemon.lookControl as? PokemonLookControl)?.lookTargetYaw
            val pose = pokemon.getCurrentPoseType()
            val floating = pose in PoseType.FLYING_POSES || pose in PoseType.SWIMMING_POSES
            if (floating && lookYaw != null) {
                // Nothing to plant so keep easing towards whatever it's looking at
                pokemon.stationaryTurnTarget = Mth.wrapDegrees(lookYaw)
                pokemon.stationaryTurnFloating = true
            } else if (lookYaw != null && pokemon.stationaryTurnTarget == null) {
                val offset = Mth.wrapDegrees(lookYaw - pokemon.yRot)
                val comfortableReach = pokemon.maxHeadYRot * STANDING_TURN_HEAD_REACH_FRACTION
                if (abs(offset) > comfortableReach) {
                    // Turn the body in one go so the head is comfortable looking at it
                    val turn = offset - Math.copySign(comfortableReach, offset)
                    pokemon.stationaryTurnTarget = Mth.wrapDegrees(pokemon.yRot + turn)
                    pokemon.stationaryTurnFloating = false
                }
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
        if (target.z > 2.5F) {
            pokemon.heading.steer(target.x, target.y, PokemonMoveControl.STANDING_TURN_BLEND_SECONDS)
        } else if (target.z > 1.5F) {
            pokemon.heading.steer(target.x, target.y, PokemonMoveControl.FLOATING_TURN_BLEND_SECONDS)
        } else if (target.z > 0F) {
            pokemon.heading.steer(target.x, target.y, pokemon.entityData.get(PokemonEntity.HEADING_TRAVEL_BLEND))
        } else {
            // Ease rather than snap so stationary turns from the server don't jump
            pokemon.heading.settle(pokemon.yRot, snap = false)
        }
        // Keep continuous with last tick so plain lerps between yBodyRotO and yBodyRot don't spin at the wrap
        pokemon.yBodyRot = pokemon.yBodyRotO + Mth.wrapDegrees(pokemon.heading.yaw.value - pokemon.yBodyRotO)
    }
}
