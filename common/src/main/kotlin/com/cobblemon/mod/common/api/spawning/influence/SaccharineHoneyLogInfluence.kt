/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.RotatedPillarBlock

class SaccharineHoneyLogInfluence(val pos: BlockPos? = null) : SpawningInfluence {

    companion object {
        const val SACCHARINE_HONEY_MARKER = "saccharine_honey_log"
    }

    var used = false
    val chanceForHA = 0.05

    override fun affectSpawnablePosition(spawnablePosition: SpawnablePosition) {
        spawnablePosition.markers.add(SACCHARINE_HONEY_MARKER)
    }

    override fun affectSpawn(action: SpawnAction<*>, entity: Entity) {
        if (entity is PokemonEntity) {
            if (Math.random() <= chanceForHA) {
                FishingSpawnCause.alterHAAttempt(entity)
            }
            PokemonProperties.parse("shiny").apply(entity)

            if (!used) {
                val logPos = pos
                val level = action.spawnablePosition.world.level
                if (logPos != null) {
                    Cobblemon.LOGGER.info("Converting honeyed log at $logPos to saccharine log")
                    val blockState = level.getBlockState(logPos)
                    if (blockState.block == CobblemonBlocks.SACCHARINE_HONEY_LOG) {
                        val axis = blockState.getValue(RotatedPillarBlock.AXIS)
                        val newState = CobblemonBlocks.SACCHARINE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis)
                        level.setBlock(logPos, newState, 3)
                    }
                }
                used = true
            }
        }
    }

    override fun affectWeight(detail: SpawnDetail, spawnablePosition: SpawnablePosition, weight: Float): Float {
        return super.affectWeight(detail, spawnablePosition, weight)
    }
}
