/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.prospecting

import com.cobblemon.mod.common.CobblemonPoiTypes
import com.cobblemon.mod.common.api.fishing.SpawnBait
import com.cobblemon.mod.common.api.spawning.influence.BucketNormalizingInfluence
import com.cobblemon.mod.common.api.spawning.influence.SpatialSpawningZoneInfluence
import com.cobblemon.mod.common.api.spawning.influence.SpawnBaitInfluence
import com.cobblemon.mod.common.api.spawning.influence.SpawningZoneInfluence
import com.cobblemon.mod.common.api.spawning.influence.UnconditionalSpawningZoneInfluence
import com.cobblemon.mod.common.api.spawning.influence.detector.SpawningInfluenceDetector
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput
import com.cobblemon.mod.common.block.LureCakeBlock
import com.cobblemon.mod.common.block.entity.LureCakeBlockEntity
import com.cobblemon.mod.common.util.math.pow
import com.cobblemon.mod.common.util.toBlockPos
import kotlin.math.ceil
import kotlin.math.sqrt
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.level.block.state.BlockState

object LureCakeDetector : SpawningInfluenceDetector {
    @JvmField
    val RANGE: Int = 48

    override fun detectFromInput(spawner: Spawner, input: SpawningZoneInput): MutableList<SpawningZoneInfluence> {
        val world = input.world
        val listOfInfluences = mutableListOf<SpawningZoneInfluence>()

        val searchRange = RANGE + ceil(sqrt(((input.length pow 2) + (input.width pow 2)).toDouble())).toInt()
        val lureCakePositions = world.poiManager.findAll(
            { holder: Holder<PoiType> -> holder.`is`(CobblemonPoiTypes.LURE_CAKE_KEY) },
            { true },
            input.getCenter().toBlockPos(),
            searchRange,
            PoiManager.Occupancy.ANY
        ).toList()

        var highestLureTier = 0

        for (lureCakePos in lureCakePositions) {
            val blockState = world.getBlockState(lureCakePos)
            if (blockState.block !is LureCakeBlock) continue

            val blockEntity = world.getBlockEntity(lureCakePos) as? LureCakeBlockEntity ?: continue
            val baitEffects = blockEntity.getBaitEffectsFromLureCake()

            highestLureTier = maxOf(highestLureTier, baitEffects.filter { it.type == SpawnBait.Effects.RARITY_BUCKET }.maxOfOrNull { it.value }?.toInt() ?: 0)

            listOfInfluences.add(
                SpatialSpawningZoneInfluence(
                    pos = lureCakePos,
                    radius = RANGE.toFloat(),
                    influence = SpawnBaitInfluence(
                        effects = baitEffects,
                        onUsed = { time ->
                            if (time == 1) {
                                tryBiteCake(world, lureCakePos)
                            }
                        }
                    )
                )
            )
        }

        if (highestLureTier > 0) {
            listOfInfluences.add(UnconditionalSpawningZoneInfluence(influence = BucketNormalizingInfluence(tier = highestLureTier)))
        }

        return listOfInfluences
    }

    private fun tryBiteCake(world: ServerLevel, lureCakePos: BlockPos) {
       if (world.isLoaded(lureCakePos)) {
           val blockEntity = world.getBlockEntity(lureCakePos) as? LureCakeBlockEntity ?: return
           blockEntity.bites++

           if (blockEntity.bites >= blockEntity.maxBites) {
               world.removeBlock(lureCakePos, false)
           } else {
               blockEntity.setChanged()
               world.sendBlockUpdated(lureCakePos, blockEntity.blockState, blockEntity.blockState, 3)
           }
        }
    }

    override fun detectFromBlock(
        world: ServerLevel,
        pos: BlockPos,
        blockState: BlockState
    ) = emptyList<SpawningZoneInfluence>()
}