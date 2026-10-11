/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.InertialEase
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.control.LookControl

/**
 * Controls head movement of pokemon. The head is clamped to the body within the look itself, and
 * [PokemonBodyRotationControl] turns the body using [lookTargetYaw].
 */
class PokemonLookControl(private val pokemon: PokemonEntity) : LookControl(pokemon) {
    companion object {
        const val HEAD_BLEND_SECONDS = 0.5F
    }

    private val headYaw = InertialEase(HEAD_BLEND_SECONDS, isAngle = true)
    private val headPitch = InertialEase(HEAD_BLEND_SECONDS, isAngle = true)
    private var headStarted = false

    /** Yaw towards what the pokemon is looking at, null when it isn't looking at anything */
    var lookTargetYaw: Float? = null
        private set

    override fun tick() {
        lookTargetYaw = null
        // Start from where the head already is instead of turning in from zero
        if (!headStarted) {
            headYaw.snap(mob.yHeadRot)
            headPitch.snap(mob.xRot)
            headStarted = true
        }

        // Focus on where it's going while travelling looking  starts back up once it stops
        if (!pokemon.navigation.isDone || pokemon.moveControl.hasWanted()) {
            this.lookAtCooldown = 0
        }

        // nothing to look at so face forward and level out
        var targetYaw = mob.yBodyRot
        var targetPitch = 0F
        val maxHeadTurn = mob.maxHeadYRot.toFloat()
        if (this.lookAtCooldown > 0) {
            --this.lookAtCooldown
            getYRotD().ifPresent { lookYaw ->
                lookTargetYaw = lookYaw
                val offset = Mth.wrapDegrees(lookYaw - mob.yBodyRot)
                targetYaw = mob.yBodyRot + Mth.clamp(offset, -maxHeadTurn, maxHeadTurn)
            }
            getXRotD().ifPresent { lookPitch -> targetPitch = lookPitch }
        }

        headYaw.update(Mth.wrapDegrees(targetYaw))
        headPitch.update(targetPitch)
        mob.yHeadRot = mob.yBodyRot + Mth.clamp(Mth.wrapDegrees(headYaw.value - mob.yBodyRot), -maxHeadTurn, maxHeadTurn)
        mob.setXRot(headPitch.value)
    }
}
