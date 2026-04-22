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
import java.util.function.Consumer

/**
 * Controls head movement of pokemon
 */
class PokemonLookControl(private val pokemon: PokemonEntity) : LookControl(pokemon) {
    override fun tick() {
        if (this.resetXRotOnTick()) {
            this.mob.setXRot(0.0f)
        }

        if (this.lookAtCooldown > 0) {
            --this.lookAtCooldown
            val targetYRotOpt = getYRotD()
            if (targetYRotOpt.isPresent) {
                val targetYRot = targetYRotOpt.get()
                val attemptedRot = rotateTowards(mob.yHeadRot, targetYRot, yMaxRotSpeed)
                val headTurnAngle = Mth.wrapDegrees(attemptedRot - mob.yBodyRot)
                val headTurn = Mth.clamp(headTurnAngle, -this.mob.maxHeadYRot.toFloat(), this.mob.maxHeadYRot.toFloat())
                val bodyTurn = headTurnAngle - headTurn // Whats left over for the head to turn

                mob.yHeadRot = mob.yBodyRot + headTurn

            }
            getXRotD().ifPresent { targetXRot ->
                mob.setXRot(rotateTowards(mob.getXRot(), targetXRot, xMaxRotAngle))
            }
        } else {
            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0f)
        }
        // Get diff between head and body

        this.clampHeadRotationToBody()
    }

    override fun clampHeadRotationToBody() {
        if (!this.mob.getNavigation().isDone) { //TODO: Is this needed?
            // Stolen from Mth.rotateIfNecessary()
            val f = Mth.degreesDifference(this.mob.yHeadRot, this.mob.yBodyRot)
            val g = Mth.clamp(f, -this.mob.getMaxHeadYRot().toFloat(), this.mob.getMaxHeadYRot().toFloat())
            this.mob.yHeadRot = this.mob.yBodyRot - g
            this.mob.yBodyRot = this.mob.yBodyRot - (f - g)
        }
    }
}