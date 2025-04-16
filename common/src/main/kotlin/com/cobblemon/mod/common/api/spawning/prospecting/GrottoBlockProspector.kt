/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.prospecting

import com.cobblemon.mod.common.CobblemonPoiTypes
import com.cobblemon.mod.common.api.habitats.Habitats
import com.cobblemon.mod.common.api.spawning.influence.GrottoInfluence
import com.cobblemon.mod.common.api.spawning.influence.WorldSlicedSpatialSpawningInfluence
import com.cobblemon.mod.common.api.spawning.influence.WorldSlicedSpawningInfluence
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.api.spawning.spawner.SpawningArea
import com.cobblemon.mod.common.block.GrottoBlock
import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import com.cobblemon.mod.common.util.math.pow
import com.cobblemon.mod.common.util.toBlockPos
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.ceil
import kotlin.math.sqrt

object GrottoBlockProspector : SpawningInfluenceProspector {
    @JvmField
    val RANGE: Int = 48

    // todo going with this prospect method for now since the GrottoSpawner might be what I want instead of handling it here
    override fun prospect(spawner: Spawner, area: SpawningArea): MutableList<WorldSlicedSpawningInfluence> {
        return mutableListOf() // GrottoSpawner handles spawning directly now
    }

    // todo this was some test code of a prospect function that would try to alter the already filtered spawn lists, but I doubt we want to go this direction
    /*override fun prospect(spawner: Spawner, area: SpawningArea): MutableList<WorldSlicedSpawningInfluence> {
        val world = area.world
        val listOfInfluences = mutableListOf<WorldSlicedSpawningInfluence>()

        val searchRange = RANGE + ceil(sqrt(((area.length pow 2) + (area.width pow 2)).toDouble())).toInt()
        val grottoBlockPositions = world.poiManager.findAll({ holder: Holder<PoiType> -> holder.`is`(CobblemonPoiTypes.GROTTO_BLOCK_KEY) },{ true },area.getCenter().toBlockPos(),searchRange, PoiManager.Occupancy.ANY).toList()

        for (grottoBlockPos in grottoBlockPositions) {
            val blockState = world.getBlockState(grottoBlockPos)
            if (blockState.block !is GrottoBlock) continue

            val blockEntity = world.getBlockEntity(grottoBlockPos) as? GrottoBlockEntity ?: continue
            val structureId = blockEntity.detectedStructure ?: continue

            if (structureId == null) {
                println("[GrottoBlockProspector] Grotto at $grottoBlockPos has no detected structure.")
                continue
            }

            val habitat = Habitats.getHabitat(structureId)
            if (habitat == null) {
                println("[GrottoBlockProspector] Grotto at $grottoBlockPos references unknown habitat: $structureId")
                continue
            }

            println("[GrottoBlockProspector] Found Grotto at $grottoBlockPos")
            println("  ↳ Structure: $structureId")
            if (habitat.spawnPool.isEmpty()) {
                println("  ↳ Spawn pool is empty.")
            } else {
                println("  ↳ Spawn pool:")
                habitat.spawnPool.forEach {
                    println("      • $it")
                }
            }

            val allowedSpawns = blockEntity.currentSpawnGroup
            if (allowedSpawns.isNotEmpty()) {
                listOfInfluences.add(
                        WorldSlicedSpatialSpawningInfluence(
                                grottoBlockPos,
                                RANGE.toFloat(),
                                influence = GrottoInfluence(structureId, allowedSpawns)
                        )
                )
            }
        }

        return listOfInfluences
    }*/

    override fun prospectBlock(world: ServerLevel, pos: BlockPos, blockState: BlockState): WorldSlicedSpatialSpawningInfluence? { return null }
}