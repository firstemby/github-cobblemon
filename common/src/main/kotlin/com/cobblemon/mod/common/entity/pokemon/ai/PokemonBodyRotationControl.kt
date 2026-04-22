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
import kotlin.math.abs
import net.minecraft.util.Mth.lerp

/**
 * Controls body movement of pokemon
 */
class PokemonBodyRotationControl(private val pokemon: PokemonEntity) : BodyRotationControl(pokemon) {
    override fun clientTick() {
        if (this.isMoving()) {
            this.mob.yBodyRot = this.mob.getYRot();
            this.rotateHeadIfNecessary();
            this.lastStableYHeadRot = this.mob.yHeadRot;
            this.headStableTime = 0;
        } else {
            val bodyToHeadDiff = Mth.wrapDegrees(pokemon.yHeadRot - pokemon.yBodyRot )
            if(this.mob.maxHeadYRot.toFloat() - abs(bodyToHeadDiff) < 1e-3) { // Poor check this should probably be done in the lookControl when clamping happens
                //TODO: a turn without big jumps might be really good for fliers but grounded mons need the large non slidey adjustments
                val poseTypes: Set<PoseType> = setOf(PoseType.FLY, PoseType.HOVER, PoseType.SWIM, PoseType.FLOAT)
                if(pokemon.getCurrentPoseType() !in poseTypes) {
                    pokemon.yBodyRot += bodyToHeadDiff * 0.8f
                } else {
                    pokemon.yBodyRot += bodyToHeadDiff * 0.1f
                }
                pokemon.yRot = pokemon.yBodyRot
            }
        }
    }
}