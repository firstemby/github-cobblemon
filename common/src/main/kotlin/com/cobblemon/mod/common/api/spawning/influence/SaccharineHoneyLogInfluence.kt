/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity

class SaccharineHoneyLogInfluence(val pos: BlockPos? = null) : SpawningInfluence {

    var used = false
    val chanceForHA = 0.05

    private fun markUsed() {
        used = true // todo even though I call this it is not saved.... hmmmm
    }

    override fun affectSpawn(entity: Entity) {
        super.affectSpawn(entity)
        if (entity is PokemonEntity) {
            if (Math.random() <= chanceForHA) {
                FishingSpawnCause.alterHAAttempt(entity)
            }
            this.markUsed()
        }
    }

    override fun affectWeight(detail: SpawnDetail, ctx: SpawningContext, weight: Float): Float {
        return super.affectWeight(detail, ctx, weight)
    }

    fun wasUsed(): Boolean = used
}
