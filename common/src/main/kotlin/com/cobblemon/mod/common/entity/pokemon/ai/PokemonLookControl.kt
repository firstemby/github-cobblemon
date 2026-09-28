/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.control.LookControl

/**
 * Controls head movement of pokemon. The head is clamped to the body within the look itself, and whatever
 * the head can't cover is left in [bodyTurnRequest] for [PokemonBodyRotationControl].
 */
class PokemonLookControl(private val pokemon: PokemonEntity) : LookControl(pokemon) {
    /** Signed degrees the look target is past the head's yaw limit this tick. */
    var bodyTurnRequest = 0F
        private set

    override fun tick() {
        bodyTurnRequest = 0F
        if (this.resetXRotOnTick()) {
            this.mob.setXRot(0.0f)
        }

        if (this.lookAtCooldown > 0) {
            --this.lookAtCooldown
            getYRotD().ifPresent { targetYRot ->
                val attemptedRot = rotateTowards(mob.yHeadRot, targetYRot, yMaxRotSpeed)
                val headTurnAngle = Mth.wrapDegrees(attemptedRot - mob.yBodyRot)
                val maxHeadTurn = mob.maxHeadYRot.toFloat()
                val headTurn = Mth.clamp(headTurnAngle, -maxHeadTurn, maxHeadTurn)
                bodyTurnRequest = headTurnAngle - headTurn
                mob.yHeadRot = mob.yBodyRot + headTurn
            }
            getXRotD().ifPresent { targetXRot ->
                mob.setXRot(rotateTowards(mob.getXRot(), targetXRot, xMaxRotAngle))
            }
        } else {
            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0f)
        }

        this.clampHeadRotationToBody()
    }
}
