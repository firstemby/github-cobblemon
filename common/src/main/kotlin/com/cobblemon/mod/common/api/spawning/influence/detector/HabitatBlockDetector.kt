/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence.detector

import com.cobblemon.mod.common.CobblemonPoiTypes
import com.cobblemon.mod.common.api.spawning.influence.HabitatImpactedSpawningInfluence
import com.cobblemon.mod.common.api.spawning.influence.SpawningZoneInfluence
import com.cobblemon.mod.common.api.spawning.influence.UnconditionalSpawningZoneInfluence
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput
import com.cobblemon.mod.common.util.toBlockPos
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.level.block.state.BlockState

object HabitatBlockDetector : SpawningInfluenceDetector {
    @JvmField
    val RANGE: Int = 48

    override fun detectFromInput(spawner: Spawner, input: SpawningZoneInput): List<SpawningZoneInfluence> {
        val world = input.world
        val searchRange = maxOf(RANGE, input.length * 2, input.height * 2)
        val habitatBlockPositions = world.poiManager.findAll(
            { holder: Holder<PoiType> -> holder.`is`(CobblemonPoiTypes.HABITAT_BLOCK_KEY) },
            { true },
            input.getCenter().toBlockPos(),
            searchRange,
            PoiManager.Occupancy.ANY
        ).toList()

        val structureIds = emptySet<ResourceLocation>()

        return if (habitatBlockPositions.isEmpty()) {
            emptyList()
        } else {
            listOf(
                UnconditionalSpawningZoneInfluence(
                    influence = HabitatImpactedSpawningInfluence(
                        structureIds = structureIds
                    )
                )
            )
        }
    }

    override fun detectFromBlock(world: ServerLevel, pos: BlockPos, blockState: BlockState) = emptyList<SpawningZoneInfluence>()
}