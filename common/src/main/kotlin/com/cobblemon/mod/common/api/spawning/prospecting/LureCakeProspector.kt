/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.prospecting

import com.cobblemon.mod.common.CobblemonPoiTypes
import com.cobblemon.mod.common.api.spawning.influence.SpawnBaitInfluence
import com.cobblemon.mod.common.api.spawning.influence.WorldSlicedSpatialSpawningInfluence
import com.cobblemon.mod.common.api.spawning.influence.WorldSlicedSpawningInfluence
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.api.spawning.spawner.SpawningArea
import com.cobblemon.mod.common.block.LureCakeBlock
import com.cobblemon.mod.common.block.entity.LureCakeBlockEntity
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

object LureCakeProspector : SpawningInfluenceProspector {
    @JvmField
    val RANGE: Int = 48

    override fun prospect(spawner: Spawner, area: SpawningArea): MutableList<WorldSlicedSpawningInfluence> {
        val world = area.world
        val listOfInfluences = mutableListOf<WorldSlicedSpawningInfluence>()

        val searchRange = RANGE + ceil(sqrt(((area.length pow 2) + (area.width pow 2)).toDouble())).toInt()
        val lureCakePositions = world.poiManager.findAll({ holder: Holder<PoiType> -> holder.`is`(CobblemonPoiTypes.LURE_CAKE_KEY) },{ true },area.getCenter().toBlockPos(),searchRange, PoiManager.Occupancy.ANY).toList()

        for (lureCakePos in lureCakePositions) {
            val blockState = world.getBlockState(lureCakePos)
            if (blockState.block !is LureCakeBlock) continue

            val blockEntity = world.getBlockEntity(lureCakePos) as? LureCakeBlockEntity ?: continue
            val baitEffects = blockEntity.getBaitEffectsFromLureCake()

            listOfInfluences.add(WorldSlicedSpatialSpawningInfluence(lureCakePos, RANGE.toFloat(), influence = SpawnBaitInfluence(baitEffects, lureCakePos)))
        }

        return listOfInfluences
    }

    override fun prospectBlock(world: ServerLevel, pos: BlockPos, blockState: BlockState): WorldSlicedSpatialSpawningInfluence? { return null }
}