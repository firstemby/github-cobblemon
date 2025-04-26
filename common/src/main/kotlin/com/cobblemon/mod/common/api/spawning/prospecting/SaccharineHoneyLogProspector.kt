/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.prospecting

import com.cobblemon.mod.common.CobblemonPoiTypes
import com.cobblemon.mod.common.api.spawning.influence.SaccharineHoneyLogInfluence
import com.cobblemon.mod.common.api.spawning.influence.WorldSlicedSpatialSpawningInfluence
import com.cobblemon.mod.common.api.spawning.influence.WorldSlicedSpawningInfluence
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.api.spawning.spawner.SpawningArea
import com.cobblemon.mod.common.util.math.pow
import com.cobblemon.mod.common.util.toBlockPos
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.sqrt

object SaccharineHoneyLogProspector : SpawningInfluenceProspector {
    @JvmField
    val RANGE: Int = 32

    override fun prospect(spawner: Spawner, area: SpawningArea): MutableList<WorldSlicedSpawningInfluence> {
        val world = area.world
        val listOfInfluences = mutableListOf<WorldSlicedSpawningInfluence>()

        val searchRange = RANGE + ceil(sqrt(((area.length pow 2) + (area.width pow 2)).toDouble())).toInt()
        val centerPos = area.getCenter().toBlockPos()

        val honeyLogPositions = world.poiManager.findAll(
            { holder: Holder<PoiType> -> holder.`is`(CobblemonPoiTypes.SACCHARINE_HONEY_LOG_KEY) },
            { true },
            centerPos,
            searchRange,
            PoiManager.Occupancy.ANY
        ).toList()

        for (pos in honeyLogPositions) {
            val influence = WorldSlicedSpatialSpawningInfluence(pos, RANGE.toFloat(), SaccharineHoneyLogInfluence(pos))
            listOfInfluences.add(influence)
        }

        return listOfInfluences
    }

    override fun prospectBlock(world: ServerLevel, pos: BlockPos, blockState: BlockState): WorldSlicedSpatialSpawningInfluence? { return null }
}
